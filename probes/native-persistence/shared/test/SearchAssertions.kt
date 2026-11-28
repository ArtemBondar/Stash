package org.example.stashprobe

import kotlin.test.assertEquals

private val matching = ProbeItem("1", "Original https://example.org/100%_done café")
private val other = ProbeItem("2", "Original https://example.org/100XXdone")

suspend fun insertSearchFixtures(dao: ProbeDao) {
    dao.insert(matching)
    dao.insert(other)
}

suspend fun assertLiteralSearch(dao: ProbeDao) {
    assertEquals(listOf(matching), dao.search("https://example.org/100%_done café"))
    assertEquals(emptyList(), dao.search("missing"))
    assertEquals(listOf(matching), dao.search("café"))
    assertEquals(emptyList(), dao.search("CAFÉ"))
    assertEquals(emptyList(), dao.search("cafe\u0301"))
    assertEquals(listOf(matching), dao.search("%"))
    assertEquals(listOf(matching), dao.search("_"))
    assertEquals(listOf(matching, other), dao.search(""))
}
