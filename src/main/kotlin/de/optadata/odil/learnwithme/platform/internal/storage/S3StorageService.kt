package de.optadata.odil.learnwithme.platform.internal.storage

import de.optadata.odil.learnwithme.platform.StorageService
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

/**
 * Objektspeicher-Adapter (§6.2). Speichert unter `{workspaceId}/{uuid}-{filename}`, damit eine
 * Kontolöschung später gezielt alle Objekte eines Workspace per Präfix-Listing entfernen kann
 * (siehe Hinweis in `identity.internal.service.AccountService` — noch nicht verdrahtet, Epic B
 * liefert nur den Speicher-Port selbst).
 */
@Service
class S3StorageService(
    private val s3Client: S3Client,
    @Value("\${learnwithme.storage.bucket}") private val bucket: String,
) : StorageService {

    @PostConstruct
    fun ensureBucketExists() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        } catch (_: BucketAlreadyOwnedByYouException) {
            // Bucket existiert bereits — normal bei jedem Neustart.
        }
    }

    override fun store(workspaceId: UUID, filename: String, bytes: ByteArray): String {
        val key = "$workspaceId/${UUID.randomUUID()}-${sanitize(filename)}"
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromBytes(bytes),
        )
        return key
    }

    override fun load(key: String): ByteArray =
        s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()).readAllBytes()

    override fun delete(key: String) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    private fun sanitize(filename: String): String = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
