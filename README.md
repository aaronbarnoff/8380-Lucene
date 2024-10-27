Phase 2:


arXivScraper.py: Uses arxiv's API to download metadata for CS papers and save them each "arxivPapers" as a text file [arxiv_ID].txt. Default amount of papers to download is 10000. Can specify start/end on lines 13-15. The arxivScraperOAI is mainly just for reference if anyone is interested. I don't think it is as useful since OAI doesn't seem to provide PDF links.


Phase 1:
IndexAllFilesInDirectory.java just requires indexPath for where to put the index, and docsPath for where the source documents are (lines 20 and 21).

GUI.java just needs the index path on line 14. 

The dependencies I used should only be from "modules" and "modules-thirdparty" that Lucene 9.12 provides.
