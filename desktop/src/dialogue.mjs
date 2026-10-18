// Book dialogue is source data. No AI-generated turns are passed off as quotes.
export function parseDialogue(source, page) {
  const turns = [];
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    const match = line.match(
      /^([\p{L}][\p{L}\p{N} .·_-]{0,30})\s*[:：]\s*(.+)$/u,
    );
    if (match && /[가-힣]/.test(match[2]))
      turns.push({ speaker: match[1].trim(), text: match[2].trim(), page });
  }
  return turns;
}
export function dialogueForPage(book, page, source) {
  const supplied = (book.chapters || [])
    .flatMap((c) => c.sections || [])
    .flatMap((s) => s.turns || [])
    .filter((t) => t.page === page);
  return supplied.length ? supplied : parseDialogue(source, page);
}
export function speakersOf(turns) {
  return [...new Set(turns.map((t) => t.speaker))];
}
// Match a complete quote across split font spans, not isolated repeated words.
export function quoteSpanIndexes(strings, quote) {
  const normalize = (v) => v.replace(/\s+/g, "");
  const target = normalize(quote || "");
  if (!target) return [];
  const pieces = strings.map(normalize),
    start = pieces.join("").indexOf(target);
  if (start < 0) return [];
  const result = [];
  let offset = 0;
  pieces.forEach((p, i) => {
    if (p.length && offset < start + target.length && offset + p.length > start)
      result.push(i);
    offset += p.length;
  });
  return result;
}
