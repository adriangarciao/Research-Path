#!/usr/bin/env python3
"""ResearchPath OpenAlex metadata audit.

Three small, repeatable OpenAlex searches; generates raw snapshots, a CSV of
all works, a machine-readable summary, and a Markdown quality report.

Standard library only. Compatible with Python 3.10+.
"""
from __future__ import annotations

import argparse
import csv
from collections import Counter
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import statistics
import sys
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

DEFAULT_TOPICS = [
    "graph neural networks",
    "reinforcement learning in robotics",
    "AI-assisted medical diagnosis",
]
API = "https://api.openalex.org/works"
FIELDS = [
    "id", "title", "doi", "publication_year", "type", "topics",
    "keywords", "abstract_inverted_index", "referenced_works",
    "cited_by_count", "authorships", "open_access", "primary_location",
]


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:70] or "topic"


def as_list(v: Any) -> list:
    return v if isinstance(v, list) else []


def pct(n: int | float, d: int | float) -> float:
    return round(100 * n / d, 1) if d else 0.0


def safe_text(v: Any) -> str:
    return re.sub(r"\s+", " ", str(v or "")).strip()


def md(v: Any) -> str:
    return safe_text(v).replace("|", "\\|").replace("`", "'")


def csvsafe(v: Any) -> Any:
    if isinstance(v, str) and v.lstrip().startswith(("=", "+", "-", "@")):
        return "'" + v
    return v


def label(item: Any) -> str:
    return safe_text(item.get("display_name") or item.get("id") or "") if isinstance(item, dict) else ""


def tags(paper: dict, kind: str, floor: float) -> list[dict]:
    out = []
    for item in as_list(paper.get(kind)):
        if not isinstance(item, dict):
            continue
        value = item.get("id") or item.get("display_name")
        try:
            score = float(item.get("score", 1.0))
        except (ValueError, TypeError):
            score = 0.0
        if value and score >= floor:
            out.append(item)
    return out


def signature(paper: dict, kind: str, floor: float) -> frozenset[str]:
    return frozenset(str(t.get("id") or t.get("display_name")) for t in tags(paper, kind, floor))


def mean_jaccard(items: list[frozenset[str]]) -> float | None:
    comparisons = []
    for i, left in enumerate(items):
        for right in items[i+1:]:
            if left and right:
                comparisons.append(len(left & right) / len(left | right))
    return round(statistics.mean(comparisons), 3) if comparisons else None


def get_papers(topic: str, count: int, api_key: str | None, timeout: float) -> dict:
    params = {
        "search.title_abstract_keywords": topic,
        "per-page": count,
        "select": ",".join(FIELDS),
    }
    headers = {
        "Accept": "application/json",
        "User-Agent": "ResearchPath-Metadata-Audit/1.0 (academic metadata sampling)",
    }
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    url = API + "?" + urlencode(params)
    for attempt in range(4):
        try:
            with urlopen(Request(url, headers=headers), timeout=timeout) as response:
                body = json.load(response)
            if not isinstance(body, dict) or not isinstance(body.get("results"), list):
                raise RuntimeError("Unexpected OpenAlex response (no results array)")
            return body
        except HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")[:400]
            if error.code in (429, 500, 502, 503, 504) and attempt < 3:
                time.sleep(min(2 ** (attempt + 1), 8))
                continue
            guidance = " Set OPENALEX_API_KEY with your free activated key." if error.code in (401, 403, 429) else ""
            raise RuntimeError(f"OpenAlex HTTP {error.code}: {body}.{guidance}") from error
        except (URLError, TimeoutError) as error:
            if attempt < 3:
                time.sleep(2 ** attempt)
                continue
            raise RuntimeError(f"Could not connect to OpenAlex: {error}") from error
    raise RuntimeError("OpenAlex request failed")


def first_topic(p: dict) -> str:
    t = as_list(p.get("topics"))
    return label(t[0]) if t else "(missing)"


def summarize(topic: str, rows: list[dict], topic_floor: float, keyword_floor: float, meta: dict) -> dict:
    n = len(rows)
    t_sigs = [signature(p, "topics", topic_floor) for p in rows]
    k_sigs = [signature(p, "keywords", keyword_floor) for p in rows]
    all_topics = Counter(t.get("display_name") or t.get("id") for p in rows for t in tags(p,"topics",topic_floor))
    all_keywords = Counter(t.get("display_name") or t.get("id") for p in rows for t in tags(p,"keywords",keyword_floor))
    primary = Counter(first_topic(p) for p in rows)
    ids = {p.get("id") for p in rows if p.get("id")}
    sample_edges = sum(sum(1 for ref in as_list(p.get("referenced_works")) if ref in ids) for p in rows)
    topic_same_keyword_diff = 0
    pairs = 0
    for i in range(n):
        for j in range(i+1,n):
            if t_sigs[i] and t_sigs[j] and k_sigs[i] and k_sigs[j]:
                pairs += 1
                if t_sigs[i] == t_sigs[j] and k_sigs[i] != k_sigs[j]:
                    topic_same_keyword_diff += 1
    has_abstract = sum(bool(p.get("abstract_inverted_index")) for p in rows)
    has_topic = sum(bool(x) for x in t_sigs)
    has_keywords = sum(bool(x) for x in k_sigs)
    has_references = sum(bool(as_list(p.get("referenced_works"))) for p in rows)
    has_doi = sum(bool(p.get("doi")) for p in rows)
    has_link = sum(bool(p.get("doi") or (p.get("primary_location") or {}).get("landing_page_url") or p.get("id")) for p in rows)
    has_title = sum(bool(p.get("title")) for p in rows)
    return {
        "topic":topic, "papers":n, "provider_total_matches": meta.get("count"),
        "title_pct":pct(has_title,n), "abstract_pct":pct(has_abstract,n),
        "topics_pct":pct(has_topic,n), "keywords_pct":pct(has_keywords,n),
        "references_pct":pct(has_references,n), "doi_pct":pct(has_doi,n),
        "source_link_pct":pct(has_link,n),
        "unique_topics":len(all_topics), "unique_keywords":len(all_keywords),
        "mean_topic_tags_per_paper":round(sum(map(len,t_sigs))/n,2) if n else 0,
        "mean_keyword_tags_per_paper":round(sum(map(len,k_sigs))/n,2) if n else 0,
        "most_common_primary_topic":primary.most_common(1)[0][0] if primary else None,
        "primary_topic_dominance_pct":pct(primary.most_common(1)[0][1],n) if primary else 0,
        "avg_topic_jaccard":mean_jaccard(t_sigs),
        "avg_keyword_jaccard":mean_jaccard(k_sigs),
        "pairs_same_topics_different_keywords":topic_same_keyword_diff,
        "pairs_with_both_tag_types":pairs,
        "sample_internal_citations":sample_edges,
        "top_topics":all_topics.most_common(8),
        "top_keywords":all_keywords.most_common(12),
        "publication_types":Counter(str(p.get("type") or "unknown") for p in rows).most_common(),
        "papers_preview":[{
            "title":safe_text(p.get("title")), "id":p.get("id"),
            "year":p.get("publication_year"),
            "primary_topic":first_topic(p),
            "keywords":[label(k) for k in tags(p,"keywords",keyword_floor)[:5]],
        } for p in rows],
    }


def csv_rows(topic: str, records: list[dict], topic_floor: float, keyword_floor: float) -> list[dict]:
    out = []
    for p in records:
        authors = [label(a.get("author")) for a in as_list(p.get("authorships")) if isinstance(a, dict)]
        tt = tags(p,"topics",topic_floor)
        kk = tags(p,"keywords",keyword_floor)
        out.append({
            "search_topic":topic,
            "openalex_id":p.get("id") or "",
            "title":safe_text(p.get("title")),
            "year":p.get("publication_year") or "",
            "type":p.get("type") or "",
            "doi":p.get("doi") or "",
            "authors":"; ".join(a for a in authors if a),
            "has_abstract":bool(p.get("abstract_inverted_index")),
            "topics":"; ".join(label(t) for t in tt),
            "keywords":"; ".join(label(t) for t in kk),
            "topic_count":len(tt),
            "keyword_count":len(kk),
            "citations_received":p.get("cited_by_count") or 0,
            "references_outgoing":len(as_list(p.get("referenced_works"))),
            "open_access":(p.get("open_access") or {}).get("is_oa"),
            "source_url":p.get("doi") or (p.get("primary_location") or {}).get("landing_page_url") or p.get("id") or "",
        })
    return out


def assess(s:dict) -> list[str]:
    flags=[]
    n=s["papers"]
    if n == 0:
        return ["No papers returned; inspect the query or provider response."]
    if n < 8:
        flags.append("Very small sample: patterns will be unstable.")
    if s["abstract_pct"] < 70:
        flags.append("Abstract coverage below 70%: abstract-dependent difficulty/relevance scoring may be unreliable.")
    if s["topics_pct"] < 80:
        flags.append("Less than 80% have topics at the configured confidence cutoff.")
    if s["keywords_pct"] < 80:
        flags.append("Less than 80% have keywords at the configured confidence cutoff.")
    if s["primary_topic_dominance_pct"] >= 70:
        flags.append("At least 70% share the same primary topic: topic-only coverage may be too coarse.")
    if s["avg_topic_jaccard"] is not None and s["avg_keyword_jaccard"] is not None:
        if s["avg_topic_jaccard"]-s["avg_keyword_jaccard"] >= 0.15:
            flags.append("Keyword sets are noticeably more differentiated than topic sets; test keyword-level coverage.")
    if not flags:
        flags.append("No preset audit thresholds triggered. This is not proof of semantic relevance or good learning order.")
    return flags


def render_report(summaries: list[dict], args: argparse.Namespace, timestamp: str, live: bool) -> str:
    lines = [
        "# ResearchPath — OpenAlex Metadata Audit",
        "",
        f"**Run timestamp:** {timestamp}",
        f"**Source:** {'Live OpenAlex API' if live else 'Local input JSON snapshots (not fetched by this run)'}",
        f"**Sample target:** {args.per_topic} records per topic; top-ranked search results, not random samples",
        f"**Confidence cutoffs:** topics ≥ {args.topic_min_score:.2f}; keywords ≥ {args.keyword_min_score:.2f}",
        "",
        "## Quick comparison", "",
        "| Research query | Works | Abstracts | Topics | Keywords | Primary topic concentration | Unique keywords |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for s in summaries:
        lines.append(f"| {md(s['topic'])} | {s['papers']} | {s['abstract_pct']}% | {s['topics_pct']}% | {s['keywords_pct']}% | {s['primary_topic_dominance_pct']}% | {s['unique_keywords']} |")
    lines.extend(["", "## What the metrics mean", "",
        "- **Abstracts/topics/keywords:** Percentage of returned records with the metadata needed to build features.",
        "- **Primary topic concentration:** Percentage sharing the most common first topic; a warning for coarse classification, not a quality judgment.",
        "- **Jaccard overlap:** Average shared-tag fraction among paper pairs *where both have that tag type*. Higher means more similar metadata assignments. Different tag vocabularies mean topic-vs-keyword values are suggestive, not directly calibrated.",
        "- **Same-topic/different-keyword pairs:** Among pairs with both tag types, how often identical topic sets conceal different keyword sets.",
        "- **Internal citation edges:** Citation links between works in this tiny returned sample. Low values do not imply weak citation data globally.",
        "", "## Findings by topic", "",
    ])
    for s in summaries:
        lines.extend([
            f"### {md(s['topic'])}", "",
            f"- Works retrieved: **{s['papers']}** (provider reports {s['provider_total_matches']} total matches).",
            f"- Complete titles **{s['title_pct']}%**; abstracts **{s['abstract_pct']}%**; DOI **{s['doi_pct']}%**; source links **{s['source_link_pct']}%**.",
            f"- At least one topic **{s['topics_pct']}%**; at least one keyword **{s['keywords_pct']}%**; at least one indexed reference **{s['references_pct']}%**.",
            f"- Distinct topic labels **{s['unique_topics']}**; distinct keywords **{s['unique_keywords']}**; average tags/paper **{s['mean_topic_tags_per_paper']} topics / {s['mean_keyword_tags_per_paper']} keywords**.",
            f"- Most common primary topic: **{md(s['most_common_primary_topic'])}** ({s['primary_topic_dominance_pct']}% of sample).",
            f"- Mean pairwise Jaccard: topics **{s['avg_topic_jaccard']}**, keywords **{s['avg_keyword_jaccard']}** (None means insufficient comparable pairs).",
            f"- Same topic tags but different keyword tags: **{s['pairs_same_topics_different_keywords']} / {s['pairs_with_both_tag_types']}** eligible paper pairs; internal citation edges: **{s['sample_internal_citations']}**.",
            "", "**Most frequent topic labels:** " + (", ".join(f"{md(t)} ({n})" for t,n in s["top_topics"]) or "none"),
            "", "**Most frequent keywords:** " + (", ".join(f"{md(t)} ({n})" for t,n in s["top_keywords"]) or "none"),
            "", "**Automated diagnostic flags:**",
        ])
        for f in assess(s):
            lines.append(f"- {f}")
        lines.extend(["", "**Sample records (open and verify their relevance):**", "",
                      "| Publication | Year | Primary topic | Leading keywords |",
                      "|---|---:|---|---|"])
        for p in s["papers_preview"]:
            name=md(p["title"])[:100]
            if p.get("id", "").startswith("https://"):
                name=f"[{name}]({p['id']})"
            lines.append(f"| {name} | {p['year'] or '?'} | {md(p['primary_topic'])} | {md(', '.join(p['keywords'][:3]))} |")
        lines.append("")
    lines.extend([
        "## Decision guidance for ResearchPath", "",
        "1. **Keep broad topics as a high-level layer**, not the only coverage representation, if the same primary topic dominates a query.",
        "2. **Use finer keywords as candidate subtopic signals** if they are present, diverse, and relevant. Do not count unique tags as proof of actual learning coverage.",
        "3. **Use graceful fallbacks for missing abstracts** rather than generating difficulty labels without evidence.",
        "4. **Verify roughly 3–5 papers per topic manually** for relevance, keyword accuracy, and whether the sequence could be educational. Automated metadata checks cannot establish semantic usefulness.",
        "5. **Repeat at larger pool sizes and more varied sampling** before finalizing scoring weights. This audit targets top-ranked records only and may overestimate relevance.",
        "", "## Files generated", "",
        "- `raw/*.json` — untouched provider response snapshots for reproducibility.",
        "- `papers.csv` — one row per retrieved publication with normalized metadata fields.",
        "- `summary.json` — metrics and warning flags for automated processing.",
        "- `REPORT.md` — this human-readable report.",
        "", "## Caveats", "",
        "OpenAlex topics and keywords are model-assigned and may contain errors. Citation counts depend on age and coverage; citation links do not imply learning prerequisites. Abstract availability is not readability. Topic diversity is a metadata proxy, not evidence that a person learned more.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser=argparse.ArgumentParser(description="Audit whether OpenAlex metadata can support ResearchPath ranking and coverage optimization.")
    parser.add_argument("--topics",nargs="+",default=DEFAULT_TOPICS,help="Topic queries (quote each query)")
    parser.add_argument("--per-topic",type=int,default=10,help="Number of top results per query (1–100)")
    parser.add_argument("--output",default="researchpath_audit_output",help="Output directory")
    parser.add_argument("--input-dir",help="Instead of live API calls, load JSON files named <topic-slug>.json from this directory")
    parser.add_argument("--topic-min-score",type=float,default=0.15,help="Minimum classifier confidence for a topic tag")
    parser.add_argument("--keyword-min-score",type=float,default=0.25,help="Minimum classifier confidence for a keyword tag")
    parser.add_argument("--timeout",type=float,default=20.0)
    args=parser.parse_args()
    if not (1<=args.per_topic<=100):
        parser.error("--per-topic must be between 1 and 100")
    if any(not 0<=v<=1 for v in (args.topic_min_score,args.keyword_min_score)):
        parser.error("tag confidence scores must be between 0 and 1")
    output=Path(args.output)
    raw=output/"raw"
    raw.mkdir(parents=True,exist_ok=True)
    api_key=os.getenv("OPENALEX_API_KEY")
    if not api_key and not args.input_dir:
        print("Using OpenAlex's limited keyless access (set OPENALEX_API_KEY for more reliable use).")
    report_summaries=[]
    all_rows=[]
    for topic in args.topics:
        fn=slug(topic)+".json"
        print(f"Auditing: {topic}",flush=True)
        if args.input_dir:
            infile=Path(args.input_dir)/fn
            if not infile.exists():
                raise RuntimeError(f"Missing input snapshot {infile}")
            body=json.loads(infile.read_text(encoding="utf-8"))
        else:
            body=get_papers(topic,args.per_topic,api_key,args.timeout)
        rows=as_list(body.get("results"))[:args.per_topic]
        if not all(isinstance(row,dict) for row in rows):
            raise RuntimeError(f"Unexpected record structure for topic: {topic}")
        (raw/fn).write_text(json.dumps(body,ensure_ascii=False,indent=2),encoding="utf-8")
        s=summarize(topic,rows,args.topic_min_score,args.keyword_min_score,body.get("meta") or {})
        report_summaries.append(s)
        all_rows.extend(csv_rows(topic,rows,args.topic_min_score,args.keyword_min_score))
        print(f"  {len(rows)} works · {s['unique_topics']} distinct topics · {s['unique_keywords']} distinct keywords · abstracts in {s['abstract_pct']}%")
    if all_rows:
        with (output/"papers.csv").open("w",newline="",encoding="utf-8-sig") as file:
            writer=csv.DictWriter(file,fieldnames=list(all_rows[0]))
            writer.writeheader()
            writer.writerows({k:csvsafe(v) for k,v in row.items()} for row in all_rows)
    else:
        (output/"papers.csv").write_text("",encoding="utf-8")
    timestamp=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    machine={"created_at":timestamp,"live_fetch":not bool(args.input_dir),"tag_cutoffs":{"topic":args.topic_min_score,"keyword":args.keyword_min_score},
             "topics":[dict(s,flags=assess(s)) for s in report_summaries]}
    (output/"summary.json").write_text(json.dumps(machine,ensure_ascii=False,indent=2),encoding="utf-8")
    (output/"REPORT.md").write_text(render_report(report_summaries,args,timestamp,not bool(args.input_dir)),encoding="utf-8")
    print(f"\nAudit complete. View {output/'REPORT.md'} and {output/'papers.csv'}.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Audit failed: {exc}",file=sys.stderr)
        sys.exit(1)
