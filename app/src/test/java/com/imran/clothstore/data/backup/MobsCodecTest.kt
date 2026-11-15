package com.imran.clothstore.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MobsCodec-এর যুক্তি যাচাই (v1.1.6) — নাম্বার আলাদা করা/মোছা/ফেরত বসানো
 * ঠিকমতো না হলে হয় নাম্বার ফাঁস হবে, নয়তো বৈধ ব্যবহারকারীর নাম্বার হারাবে।
 */
class MobsCodecTest {

    private fun entry(id: Long, mob: String) =
        BackupEntry(id = id, name = "পার্টি$id", mob = mob)

    private fun sample() = BackupPayload(
        is_c1_list = listOf(entry(1L, "+8801711111111"), entry(2L, "")),
        is_c2_list = listOf(entry(3L, "+8801722222222")),
        is_c3_list = emptyList(),
        is_c4_list = listOf(entry(4L, "+8801733333333")),
        is_deleted_ids = mapOf("is_c1_list" to listOf(9L))
    )

    @Test
    fun extractCollectsNonBlankMobsFromAllLists() {
        assertEquals(
            mapOf(
                1L to "+8801711111111",
                3L to "+8801722222222",
                4L to "+8801733333333"
            ),
            MobsCodec.extractMobs(sample())
        )
    }

    @Test
    fun stripClearsEveryMobButKeepsAllEntries() {
        val stripped = MobsCodec.stripMobs(sample())
        assertTrue(MobsCodec.extractMobs(stripped).isEmpty())
        assertEquals(setOf(1L, 2L, 3L, 4L), MobsCodec.entryIds(stripped))
    }

    @Test
    fun applyRestoresMobsById() {
        val mobs = MobsCodec.extractMobs(sample())
        val restored = MobsCodec.applyMobs(MobsCodec.stripMobs(sample()), mobs)
        assertEquals(mobs, MobsCodec.extractMobs(restored))
    }

    @Test
    fun applyLeavesEntriesWithoutMapEntryUntouched() {
        val applied = MobsCodec.applyMobs(sample(), mapOf(2L to "+8801799999999"))
        assertEquals("+8801799999999", applied.is_c1_list[1].mob)
        assertEquals("+8801711111111", applied.is_c1_list[0].mob)
    }

    @Test
    fun tombstonedIdsFlattensAllKeys() {
        assertEquals(setOf(9L), MobsCodec.tombstonedIds(sample()))
    }
}
