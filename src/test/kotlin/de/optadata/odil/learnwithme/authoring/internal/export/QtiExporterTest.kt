package de.optadata.odil.learnwithme.authoring.internal.export

import de.optadata.odil.learnwithme.authoring.internal.domain.BloomLevel
import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

private fun item(type: ItemType, stem: String, payloadJson: String) = Item(
    workspaceId = UUID.randomUUID(),
    conceptId = UUID.randomUUID(),
    type = type,
    stem = stem,
    payload = payloadJson,
    explanation = "Weil.",
    bloomLevel = BloomLevel.APPLY,
    sourceChunkId = UUID.randomUUID(),
    sourceCharFrom = 0,
    sourceCharTo = 10,
)

/** Kein QTI-2.1-XSD lokal verfügbar (kein HTTPS-Egress in dieser Sandbox, siehe docs/progress.md
 * Epic F) — daher keine echte Schema-Validierung. Was hier geprüft wird: jede erzeugte Datei ist
 * wohlgeformtes XML, und die fachlich wichtigen Werte (korrekte Antwort(en)) landen an der laut
 * QTI-2.1-Spezifikation richtigen Stelle (`correctResponse`/`responseProcessing`). */
class QtiExporterTest {

    private val qtiNs = "http://www.imsglobal.org/xsd/imsqti_v2p1"
    private val xpath = XPathFactory.newInstance().newXPath().apply {
        namespaceContext = object : NamespaceContext {
            override fun getNamespaceURI(prefix: String?) = if (prefix == "qti") qtiNs else null
            override fun getPrefix(namespaceURI: String?) = if (namespaceURI == qtiNs) "qti" else null
            override fun getPrefixes(namespaceURI: String?) = null
        }
    }

    private fun parse(xml: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }

    private fun singleEntryXml(item: Item): ByteArray {
        ZipInputStream(ByteArrayInputStream(QtiExporter.toZip(listOf(item)))).use { zis ->
            zis.nextEntry
            return zis.readBytes()
        }
    }

    private fun Document.string(expr: String): String = xpath.evaluate(expr, this, XPathConstants.STRING) as String
    private fun Document.nodes(expr: String): NodeList = xpath.evaluate(expr, this, XPathConstants.NODESET) as NodeList
    private fun Document.count(expr: String): Int = (xpath.evaluate("count($expr)", this, XPathConstants.NUMBER) as Double).toInt()

    @Test
    fun `one zip entry per item`() {
        val items = listOf(
            item(type = ItemType.TRUE_FALSE, stem = "A", payloadJson = """{"statement":"A","answer":true,"rationale":"r"}"""),
            item(type = ItemType.NUMERIC, stem = "B", payloadJson = """{"value":1,"tolerance":0}"""),
        )
        var count = 0
        ZipInputStream(ByteArrayInputStream(QtiExporter.toZip(items))).use { zis ->
            while (zis.nextEntry != null) count++
        }
        count shouldBe 2
    }

    @Test
    fun `MC_SINGLE produces well-formed XML with the correct choice identifier in correctResponse`() {
        val payload = """{"options":[{"id":"a","text":"3NF","correct":false,"rationale":"r1"},
            |{"id":"b","text":"2NF","correct":true,"rationale":"r2"}]}""".trimMargin()
        val doc = parse(singleEntryXml(item(type = ItemType.MC_SINGLE, stem = "Welche Form?", payloadJson = payload)))

        // Index 1 (zweite Option, 0-basiert) ist "correct":true -> Identifier C1.
        doc.string("//qti:correctResponse/qti:value/text()") shouldBe "C1"
        doc.count("//qti:simpleChoice") shouldBe 2
    }

    @Test
    fun `TRUE_FALSE with answer false yields correctResponse FALSE`() {
        val payload = """{"statement":"Die Erde ist eine Scheibe.","answer":false,"rationale":"Nein."}"""
        val doc = parse(singleEntryXml(item(type = ItemType.TRUE_FALSE, stem = "ignored", payloadJson = payload)))
        doc.string("//qti:correctResponse/qti:value/text()") shouldBe "FALSE"
    }

    @Test
    fun `ORDERING correctResponse lists the elements in correctOrder sequence`() {
        val payload = """{"elements":[{"id":"1","text":"a"},{"id":"2","text":"b"},{"id":"3","text":"c"}],"correctOrder":["3","1","2"]}"""
        val doc = parse(singleEntryXml(item(type = ItemType.ORDERING, stem = "Sortiere.", payloadJson = payload)))
        val values = doc.nodes("//qti:correctResponse/qti:value")
        (0 until values.length).map { values.item(it).textContent } shouldBe listOf("3", "1", "2")
    }

    @Test
    fun `MATCHING correctResponse pairs use directedPair leftId-space-rightId values`() {
        val payload = """{"left":[{"id":"l1","text":"A"}],"right":[{"id":"r1","text":"X"}],"pairs":[{"leftId":"l1","rightId":"r1"}]}"""
        val doc = parse(singleEntryXml(item(type = ItemType.MATCHING, stem = "Ordne zu.", payloadJson = payload)))
        doc.string("//qti:correctResponse/qti:value/text()") shouldBe "l1 r1"
    }

    @Test
    fun `CATEGORIZATION reuses matchInteraction with element-to-bucket directedPairs`() {
        val payload = """{"buckets":[{"id":"b1","label":"Fisch"}],"elements":[{"id":"e1","text":"Hai","bucketId":"b1"}]}"""
        val doc = parse(singleEntryXml(item(type = ItemType.CATEGORIZATION, stem = "Ordne zu.", payloadJson = payload)))
        doc.count("//qti:matchInteraction") shouldBe 1
        doc.string("//qti:correctResponse/qti:value/text()") shouldBe "e1 b1"
    }

    @Test
    fun `NUMERIC responseProcessing brackets the tolerance range around the exact value`() {
        val doc = parse(singleEntryXml(item(type = ItemType.NUMERIC, stem = "g?", payloadJson = """{"value":9.81,"tolerance":0.1,"unit":"m/s^2"}""")))
        doc.string("//qti:gte/qti:baseValue/text()").toDouble() shouldBe 9.71
        doc.string("//qti:lte/qti:baseValue/text()").toDouble() shouldBe 9.91
    }

    @Test
    fun `CODE_OUTPUT correctResponse is the exact expected output`() {
        val payload = """{"snippet":"print(1+1)","language":"python","expected":"2"}"""
        val doc = parse(singleEntryXml(item(type = ItemType.CODE_OUTPUT, stem = "?", payloadJson = payload)))
        doc.string("//qti:correctResponse/qti:value/text()") shouldBe "2"
    }

    @Test
    fun `stem text with XML-special characters is escaped into well-formed XML`() {
        // Wirft eine SAXException und lässt den Test fehlschlagen, wenn "&"/"<" nicht escaped wurden.
        val payload = """{"statement":"A & B < C","answer":true,"rationale":"r"}"""
        val doc = parse(singleEntryXml(item(type = ItemType.TRUE_FALSE, stem = "ignored", payloadJson = payload)))
        doc.string("//qti:itemBody/qti:p/text()") shouldBe "A & B < C"
    }
}
