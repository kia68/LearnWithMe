package de.optadata.odil.learnwithme.platform.internal.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

/** S3-Client für den Objektspeicher (§6.2: MinIO lokal / Hetzner-S3 prod, ADR nicht separat — reine Infra). */
@Configuration
class S3ClientConfig(
    @Value("\${learnwithme.storage.endpoint}") private val endpoint: String,
    @Value("\${learnwithme.storage.region}") private val region: String,
    @Value("\${learnwithme.storage.access-key}") private val accessKey: String,
    @Value("\${learnwithme.storage.secret-key}") private val secretKey: String,
    @Value("\${learnwithme.storage.path-style-access:true}") private val pathStyleAccess: Boolean,
) {

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
        .build()
}
