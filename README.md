# Cite Unseen: Research Summary

This repository contains the implementation of **Cite Unseen**, a plagiarism detection system researched as a 2014 Harvard Extension School ALM thesis: *Cite Unseen: A Plagiarism Detection System Using the Public Web as a Text Corpus* (Brian St. Marie).

This README summarizes the research itself (problem, method, experiments, findings), not the archive reconstruction process.

## Credential setup

Search provider credentials are intentionally **not** committed in source code.

Set these environment variables before running searches:

- `CITEUNSEEN_GOOGLE_API_KEY`
- `CITEUNSEEN_GOOGLE_CX`
- `CITEUNSEEN_YAHOO_CONSUMER_KEY`
- `CITEUNSEEN_YAHOO_CONSUMER_SECRET`
- `CITEUNSEEN_FAROO_API_KEY`
- `CITEUNSEEN_BING_ACCOUNT_KEYS` (comma-separated)

A template is provided at `.env.example`.

## Research problem

Most plagiarism detection systems rely on private corpora that include stored student submissions. That raises copyright and data retention concerns, and it requires ongoing corpus construction and maintenance.

The core research question was:

Can a plagiarism detection system remain effective if it uses only publicly searchable web corpora (via search provider APIs), without storing submitted documents in a private institutional corpus?

## Research goals

1. Demonstrate that a "public web only" external detection approach is feasible.
2. Keep detection quality reasonably competitive in precision/recall terms.
3. Build a detection method that works despite incomplete source visibility (you do not download and index the whole web yourself).
4. Deliver practical performance for real documents.

## High-level method

Cite Unseen processes a submission in four phases:

1. Pre-processing and tokenization
- Extract text (Apache Tika), normalize punctuation/formatting, tokenize into overlapping word n-grams (typically trigrams), deduplicate with hash sets/maps.

2. Web retrieval
- Query search providers per n-gram (Google, Yahoo, Bing, FAROO; plus an offline test engine).
- Parse SERPs into a provider-independent internal format.
- Use concurrent searching to reduce network latency impact.

3. Match analysis and scoring
- Merge URL hits across n-grams into unique URL candidates.
- For each URL candidate, build a signed numeric sequence over the suspect text:
  - positive weight for matched n-grams
  - negative weight for unmatched n-grams (gaps)
- Run a local sequence alignment style algorithm (modified Kadane-based positive-subarray search) to identify significant matching regions.
- Apply threshold-based filtering and optional weighting strategies (constant C factor, frequency-based match/gap weighting).

4. Presentation
- Highlight suspected copied spans in context.
- Group highlights by source URL/color.
- Show per-sequence score and similarity.
- Support click-through context against the source page for human verification.

## Key algorithmic idea

Naive similarity on raw match counts overproduces false positives, especially on long texts and common phrases. Cite Unseen addresses this by scoring **clusters** of proximal matches and penalizing gaps, rather than trusting isolated n-gram hits.

The thesis frames this as local sequence alignment adapted to web retrieval constraints:

- You usually only know that a query matched a URL, not full source positions.
- Candidate source representations are therefore partial/fragmented.
- The algorithm finds high-value positive subarrays over match/gap runs to surface likely copied passages.

## Important practical enhancement: snippet parsing

A significant issue is that common n-grams may not surface the true source URL in top-N web results.

Cite Unseen improves recall by parsing SERP description text (keyword-in-context snippets), re-tokenizing snippets, and matching those n-grams back to the suspect document. This recovers matches missing from rank-limited URL lists and enables stronger results even with small top-k retrieval windows.

## Experimental design

Dataset:
- COPSA (Corpus of Plagiarised Short Answers), 95 short answers (200-300 words) plus 5 original Wikipedia source articles.
- Plagiarism levels include near-copy, light revision, heavy revision, and non-plagiarism.

Metrics:
- Precision, Recall, F1, F2
- F1 used as primary optimization target, F2 as a secondary signal favoring recall sensitivity.

Test regimes:
1. Offline tests
- Search limited to the 5 original source documents via OfflineSearch.
- Used to isolate algorithmic effects under controlled corpus conditions.

2. Online tests
- Real web retrieval with allowances for source drift (Wikipedia changed since corpus creation).
- Evaluation uses primary/secondary source handling to account for web evolution.

Parameter sweeps:
- n in {1,2,3,4,5}
- Sequence score thresholds (typically 1-20)
- Optional C-factor and frequency weighting variants

## Core quantitative findings

### Offline baseline vs aligned scoring (n=3)

| Setting | Similarity | Precision | Recall | F1 | F2 |
|---|---:|---:|---:|---:|---:|
| Baseline (no alignment) | 0.35 | 0.47 | 0.99 | 0.64 | 0.81 |
| Sequence alignment (score=8) | 0.31 | 0.97 | 0.97 | 0.97 | 0.97 |

Interpretation: in controlled offline conditions, alignment nearly doubles quality metrics by suppressing false positives while preserving recall.

Best offline summary (from thesis Table 9):
- n=2, score=15: Precision 0.99, Recall 0.97, F1 0.98, F2 0.97 (best F1)
- n=3, score=8: Precision 0.97, Recall 0.97, F1 0.97, F2 0.97 (practical high performer)

### Online progression (n=3)

| Stage | Similarity | Precision | Recall | F1 | F2 |
|---|---:|---:|---:|---:|---:|
| Baseline (no advanced processing) | 0.97 | 0.01 | 0.98 | 0.02 | 0.05 |
| + SERP snippet parsing | 0.97 | 0.15 | 0.99 | 0.26 | 0.47 |
| + Sequence alignment (score=15) | 0.36 | 0.91 | 0.89 | 0.90 | 0.89 |

Interpretation: raw web matching has very high recall but unusable precision; snippet parsing helps, but alignment scoring is what makes results operationally credible.

### Best online settings by n (thesis Table 15)

| n | Score | Similarity | Precision | Recall | F1 | F2 |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10 | 0.06 | 0.79 | 0.60 | 0.68 | 0.63 |
| 2 | 12 | 0.24 | 0.87 | 0.91 | 0.89 | 0.90 |
| 3 | 15 | 0.36 | 0.91 | 0.89 | 0.90 | 0.89 |
| 4 | 16 | 0.39 | 0.89 | 0.91 | 0.90 | 0.91 |
| 5 | 13 | 0.39 | 0.86 | 0.92 | 0.89 | 0.91 |

Notable outcome: n=4 produced the strongest online F1/F2 balance in this setup, challenging the blanket assumption that trigrams are always optimal in web-constrained retrieval.

### Combined feature optimum (thesis Table 23)

Best combined configuration emphasized frequency-aware weighting and slight match emphasis:

- n=5
- match weighting: by frequency
- mismatch weighting: by frequency
- C factor: 1.2
- score threshold: 11
- Precision 0.85, Recall 0.95, F1 0.90, F2 0.93

The thesis notes the gain over simpler aligned settings is real but small; the major lift comes from sequence alignment itself.

## Performance findings

The implementation targeted linear behavior in the number of unique n-grams for most settings.

Observed complexity profile:
- Approx O(N) for standard processing paths
- Approx O(N log N) trend when weighting both matches and gaps by per-term frequency

Empirical performance test details:
- 187 files, from 2 to 12,720 trigrams (up to about 51 pages)
- Windows 7, 8 GB RAM, Intel i5-2410M @ 2.3 GHz
- 10 runs per file, mean reported

Reported practical outcome:
- Around 50 pages processed in under 10 seconds for core analysis stages (excluding unpredictable online search latency).

## Main conclusions

1. Public-web-only plagiarism detection is viable.
2. Sequence-based scoring is the key mechanism that turns high-recall noisy retrieval into usable precision.
3. Snippet parsing is a strong upstream boost for candidate quality.
4. Frequency weighting has limited incremental benefit relative to the alignment model itself.
5. Human review remains essential for final adjudication, but the system substantially improves reviewer signal quality.

## Constraints and threats to validity

- API throttling/suspensions are a real operational risk for high-volume automated querying.
- Search-provider API coverage is segmented; not all indexed content is uniformly accessible.
- Ground truth drift occurs in online evaluation because source pages evolve over time.
- As with most plagiarism research, corpus realism and metric interpretation depend on task assumptions.

## Future research directions identified in the thesis

1. Better sequence weighting (entropy-style weighting and related alternatives).
2. Semantic similarity extensions for paraphrase robustness.
3. Smarter n-gram commonness modeling to reduce search volume.
4. Better citation handling to reduce false positives on legitimately cited text.
5. UI improvements for overlap management and analyst workflow.

## Primary research source in this workspace

- `reconstructed-history/thesis-final.txt` (text extraction of final thesis PDF)
- Original PDF and supporting documents are under `Final/` and root-level thesis artifacts.
