#!/usr/bin/env python3
"""Genera un EPUB 3 mínimo y válido (en español, con portada) para pruebas. Solo librería estándar."""
import struct, sys, zipfile, zlib

def png(width, height, rgb):
    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="es">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:5d1c4a2e-0b6f-4f5e-9c1a-voxlib000001</dc:identifier>
    <dc:title>Libro de prueba</dc:title>
    <dc:creator>Autora de Prueba</dc:creator>
    <dc:language>es</dc:language>
    <meta property="dcterms:modified">2026-10-26T00:00:00Z</meta>
    <meta name="cover" content="cover"/>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
    <item id="c1" href="cap1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="cap2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
</package>
"""

def xhtml(title, body):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="es" lang="es">
<head><title>{title}</title></head>
<body>{body}</body>
</html>
"""

NAV = xhtml("Índice", """<nav epub:type="toc"><h1>Índice</h1><ol>
<li><a href="cap1.xhtml">Capítulo 1</a></li>
<li><a href="cap2.xhtml">Capítulo 2</a></li>
</ol></nav>""")

CAP1 = xhtml("Capítulo 1", """<h1>Capítulo 1</h1>
<p>En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo
de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor.</p>
<p>Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados,
lantejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda.</p>""")

CAP2 = xhtml("Capítulo 2", """<h1>Capítulo 2</h1>
<p>—¿De verdad vas a leer todo el libro esta noche? —preguntó Marta, sorprendida.</p>
<p>Eran las 11:45 y quedaban 312 páginas. Él sonrió y respondió que, con un buen lector,
los capítulos pasan volando.</p>""")

def main(out):
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        for name, data in [
            ("META-INF/container.xml", CONTAINER),
            ("OEBPS/content.opf", OPF),
            ("OEBPS/nav.xhtml", NAV),
            ("OEBPS/cap1.xhtml", CAP1),
            ("OEBPS/cap2.xhtml", CAP2),
            ("OEBPS/cover.png", png(120, 180, (31, 58, 95))),
        ]:
            z.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    print(f"written {out}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "sample.epub")
