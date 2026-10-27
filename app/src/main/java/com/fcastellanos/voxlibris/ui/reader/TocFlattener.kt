package com.fcastellanos.voxlibris.ui.reader

/** Entrada de índice aplanada con su profundidad, para dibujar una lista con sangría. */
data class FlatEntry<T>(val item: T, val depth: Int)

/** Recorre un árbol en preorden y devuelve la lista plana con la profundidad de cada nodo. */
fun <T> flattenTree(roots: List<T>, children: (T) -> List<T>): List<FlatEntry<T>> {
    val out = ArrayList<FlatEntry<T>>()
    fun visit(item: T, depth: Int) {
        out.add(FlatEntry(item, depth))
        children(item).forEach { visit(it, depth + 1) }
    }
    roots.forEach { visit(it, 0) }
    return out
}
