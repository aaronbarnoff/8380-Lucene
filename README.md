IndexAllFilesInDirectory.java just requires indexPath for where to put the index, and docsPath for where the source documents are (lines 20 and 21).

GUI.java just needs the index path on line 14. 

The dependencies I used should only be from "modules" and "modules-thirdparty" that Lucene 9.12 provides.
