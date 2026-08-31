---
name: Read Docs as Markdown
description: Convert PDFs/Office files to Markdown with Microsoft markitdown before reading, to save tokens
---

## Read Docs as Markdown

Reading a PDF/DOCX/XLSX/PPTX with the `Read` tool embeds the whole rendered
document (often images of every page) into context, which is very token-heavy.
Microsoft's [markitdown](https://github.com/microsoft/markitdown) converts the
file to compact Markdown/plain text first, so you read only the content — typically
a large fraction fewer tokens, and easier to grep/parse.

Use this for any local `.pdf`, `.docx`, `.pptx`, `.xlsx`, `.csv`, `.html` you need
to analyse (invoices, schedules, reports, specs). Skip it when you specifically need
to *see* the visual layout/images of a page — then use `Read` directly.

### Steps

1. **Ensure markitdown is available** (one-time):
   ```bash
   python -m pip install --quiet "markitdown[all]"
   ```
   If pip is blocked, fall back to `pdftotext -raw "file.pdf" out.txt` (poppler,
   already on this machine) for PDFs.

2. **Convert to Markdown**, writing the output into the scratchpad dir (not the repo):
   ```bash
   markitdown "C:/path/to/file.pdf" -o "<scratchpad>/file.md"
   ```
   Or as a one-liner via Python:
   ```bash
   python -c "from markitdown import MarkItDown; open(r'<scratchpad>/file.md','w',encoding='utf-8').write(MarkItDown().convert(r'C:/path/to/file.pdf').text_content)"
   ```

3. **Read the `.md`** with the `Read`/`Grep` tools instead of the original file.
   For big tabular documents, `Grep`/`python` the `.md` to pull only the rows you
   need rather than reading the whole thing.

### Notes

- `<scratchpad>` is the session scratchpad directory from the system prompt — keep
  converted files out of the repo working tree.
- markitdown preserves tables as Markdown tables and keeps reading order, which is
  more reliable than `pdftotext -layout` for multi-column invoices.
- For scanned/image-only PDFs, markitdown needs OCR (`markitdown[all]` pulls it in);
  plain `pdftotext` returns nothing for those.
