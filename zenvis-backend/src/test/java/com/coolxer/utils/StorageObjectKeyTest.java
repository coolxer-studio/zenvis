package com.coolxer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageObjectKeyTest {

    @Test
    void buildsFourDomainObjectKeys() {
        assertThat(StorageObjectKey.ragDataset(12))
                .isEqualTo("rag/datasets/12");
        assertThat(StorageObjectKey.ragPluginDataset("com.acme.demo", "docs", "operation-1"))
                .isEqualTo("rag/plugins/com.acme.demo/docs/operation-1");
        assertThat(StorageObjectKey.chat(7, "file-1", "../a/b:report?.txt"))
                .isEqualTo("chat/7/file-1/b_report_.txt");
        assertThat(StorageObjectKey.pluginPackage("com.acme.demo", "ABCDEF"))
                .isEqualTo("packages/com.acme.demo/abcdef.tar.gz");
        assertThat(StorageObjectKey.pluginCandidate("com.acme.demo", "operation-1"))
                .isEqualTo("upgrades/com.acme.demo/operation-1/candidate.tar.gz");
        assertThat(StorageObjectKey.pluginSnapshot("com.acme.demo", "operation-1"))
                .isEqualTo("upgrades/com.acme.demo/operation-1/snapshot.tar.gz");
        assertThat(StorageObjectKey.reportRevision("document-1", 3, "markdown"))
                .isEqualTo("documents/document-1/revisions/3/content.md");
        assertThat(StorageObjectKey.reportExport("document-1", 3, "pdf", "HASH", "v1"))
                .isEqualTo("documents/document-1/revisions/3/exports/v1/pdf/hash.pdf");
    }

    @Test
    void rejectsPathSegmentsAndUnsafeObjectKeys() {
        assertThatThrownBy(() -> StorageObjectKey.chat(7, "../file", "a.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.pluginPackage("com/acme", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.validate("../packages/a.tar.gz"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.validate("/absolute/object"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
