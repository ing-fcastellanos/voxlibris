package com.fcastellanos.voxlibris.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TocFlattenerTest {

    private data class Node(val name: String, val kids: List<Node> = emptyList())

    @Test
    fun `preorder with depths`() {
        val tree = listOf(
            Node("1", listOf(Node("1.1"), Node("1.2", listOf(Node("1.2.1"))))),
            Node("2"),
        )
        val flat = flattenTree(tree) { it.kids }
        assertEquals(listOf("1", "1.1", "1.2", "1.2.1", "2"), flat.map { it.item.name })
        assertEquals(listOf(0, 1, 1, 2, 0), flat.map { it.depth })
    }

    @Test
    fun `empty tree`() {
        assertEquals(emptyList<FlatEntry<Node>>(), flattenTree(emptyList<Node>()) { it.kids })
    }
}
