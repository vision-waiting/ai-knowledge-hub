from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
output = root / "knowledge-data" / "java" / "Java-Collections-Guide.pdf"
styles = getSampleStyleSheet()
styles["Title"].fontName = "Helvetica-Bold"
styles["Title"].fontSize = 24
styles["Title"].leading = 30
styles["Title"].alignment = TA_LEFT
styles["BodyText"].fontSize = 11
styles["BodyText"].leading = 18
styles["BodyText"].spaceAfter = 14
sections = [
    ("Java Collections Guide", "1. HashMap", [
        "HashMap stores key-value pairs in a hash table. It uses the key hash to choose a bucket, then compares keys using equals when resolving collisions.",
        "In Java 8 and later, heavily populated buckets can be transformed into balanced trees under implementation-specific conditions. HashMap allows one null key and multiple null values. It is not thread-safe.",
        "The default load factor is 0.75. Resizing increases table capacity when the number of entries exceeds the threshold. A good hash distribution helps lookup, insertion and removal approach constant average time."
    ]),
    ("Java Collections Guide", "2. ConcurrentHashMap", [
        "ConcurrentHashMap supports concurrent access by multiple threads. In modern Java implementations, updates use compare-and-set operations and synchronization of individual bins when needed.",
        "ConcurrentHashMap does not permit null keys or null values. It offers atomic operations such as putIfAbsent, compute and merge. These operations avoid a separate check-then-act race.",
        "Iterators are weakly consistent. They do not throw ConcurrentModificationException solely because another thread updates the map. A synchronized HashMap requires external locking for compound operations."
    ]),
    ("Java Collections Guide", "3. ArrayList and LinkedList", [
        "ArrayList stores elements in a resizable array. Access by index is constant time. Appending is amortized constant time, while insertion in the middle usually shifts elements.",
        "LinkedList stores elements in linked nodes. Finding an element by index requires traversal. Adding or removing through a positioned iterator changes nearby links, but reaching that position still has a cost.",
        "ArrayList is often preferred for iteration and random access. Choose a data structure from measured access patterns rather than assuming linked nodes always make insertion faster."
    ])
]
story = []
for index, (title, subtitle, paragraphs) in enumerate(sections):
    if index:
        story.append(PageBreak())
    story.extend([Paragraph(title, styles["Title"]), Spacer(1, 14), Paragraph(subtitle, styles["Heading1"]), Spacer(1, 12)])
    story.extend(Paragraph(text, styles["BodyText"]) for text in paragraphs)

def footer(canvas, doc):
    canvas.setStrokeColor(colors.HexColor("#d9e0de"))
    canvas.line(50, 54, A4[0] - 50, 54)
    canvas.setFont("Helvetica", 9)
    canvas.setFillColor(colors.HexColor("#52655e"))
    canvas.drawString(50, 38, "AI Knowledge Hub | Public technical verification fixture")
    canvas.drawRightString(A4[0] - 50, 38, str(doc.page))

SimpleDocTemplate(str(output), pagesize=A4, rightMargin=50, leftMargin=50, topMargin=55, bottomMargin=75).build(story, onFirstPage=footer, onLaterPages=footer)
reader = PdfReader(output)
assert len(reader.pages) == 3
assert "ConcurrentHashMap" in reader.pages[1].extract_text()
print(f"Created 3-page fixture: {output}")
try:
    import fitz
    preview = root / "output" / "pdf"
    preview.mkdir(parents=True, exist_ok=True)
    pdf = fitz.open(output)
    pdf[1].get_pixmap(matrix=fitz.Matrix(1, 1)).save(preview / "java-page-2.png")
except ImportError:
    pass
