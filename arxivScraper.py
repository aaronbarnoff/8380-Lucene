# This script fetches papers from the arxiv external server, using their API.
#     More on their API: https://info.arxiv.org/help/api/index.html and https://info.arxiv.org/help/api/user-manual.html
# Basic error checking and delays are implemented to avoid violating their flow control and DDoS protection.
# The script creates an arxivPapers folder and creates a .txt file for each record listing the metadata.
#      The arXiv metadata captured includes: ID, title, authors, creation date, categories, abstract, pdf Links

import feedparser
import urllib
import os
import time
import logging

start = 0       # Which result to start at, e.g. 0 or 10001
amount = 2000   # How many to retrieve per request; they limit this to 2000
end = 10000     # Which result to end at, e.g. 10000 or 20000

# List of all the computer science categories
csCats = "AI AR CC CE CG CL CR CV CY DB DC DL DM DS ET" 
"FL GL GR GT HC IR IT LG LO MA MM MS NA NE NI OH"
"OS PF PL RO SC SD SE SI SY"

# Ccreate the query to search all cs categories e.g. "cat:cs.AI OR cat:.SE OR ..."
catQuery = ''.join(['cat:cs.' + cat + '+OR+' for cat in csCats.split(" ")]).removesuffix('+OR+')

# Implement some basic logging, especially to ensure the server requests are correct
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
log_filename = 'arxiv_fetch.log'
logging.basicConfig(
    level=logging.INFO,      # Set the minimum logging level
    format='%(asctime)s - %(levelname)s - %(message)s',  # Format for log messages        
)

papersFolder = "arxivPapers"
os.makedirs(papersFolder,exist_ok=True)

# These settings are to help avoid triggering any kind of DDoS protection (from 429 or 503 "retry-after" requests)
request_delay = 3   # They specify a 3 second delay minimum
backoff_factor = 2  # Basic exponential backoff 
max_retries = 3     # For non 503 errors

base_url = 'http://export.arxiv.org/api/query?' # base URL for arXiv API

while start < end:
    query = 'search_query={}&start={}&max_results={}'.format(catQuery,start,amount)
    start = start + amount
    try:
        for attempt in range(max_retries):
            #logging.info(f"Initiating request.")
            response = urllib.request.urlopen(base_url + query)

            # Check status codes
            if response.getcode() == 200: # Request acknowledged.
                break  
            elif response.getcode() == [429, 503]: # This is to avoid their server interpreting our requests as a DDoS attack.
                retry_after = int(response.headers.get("Retry-After", request_delay))
                logging.warning(f"429 Too Many Requests. Retrying after {retry_after} seconds...")
                time.sleep(retry_after)
            elif 500 <= response.getcode() < 600: # Basic exponential backoff to avoid spamming them.
                logging.warning(f"Server error {response.getcode()}. Retrying...")
                time.sleep(request_delay * (backoff_factor ** attempt))
            else:
                logging.error(f"Unexpected status code {response.getcode()}. Stopping requests.")
                break
        else:
            logging.error("Max retries reached. Stopping requests.")
            break
    except urllib.error.HTTPError as e:
        logging.error(f"HTTP Error: {e.code}")
    except urllib.error.URLError as e:
        logging.error(f"URL Error: {e.reason}")

    # Parse the response using feedparser
    feed = feedparser.parse(response)
    #logging.info(feed)

    # Print out feed information
    if start == 0:
        logging.info('Feed title: {}'.format(feed.feed.title))
        logging.info('Feed last updated: {}'.format(feed.feed.updated))
        logging.info('Total results: {}'.format(feed.feed.opensearch_totalresults))
        logging.info('Items per page: {}'.format(feed.feed.opensearch_itemsperpage))
    logging.info('Start index: {}'.format(feed.feed.opensearch_startindex))

    # Run through each entry and print out information
    for entry in feed.entries:
        author_string = entry.author
        
        try:
            author_string += ' ({})'.format(entry.arxiv_affiliation) # author's affiliations
        except AttributeError:
            pass

        #logging.info(entry) # debug: to see the full record

        # make file in index for this paper
        fileName = os.path.join(papersFolder, f"arxiv_{entry.id.split('/abs/')[-1].replace('/', '_')}.txt")
        if os.path.exists(fileName):
            logging.info('Paper already exists.')
            continue

        with open(f'{fileName}', "w") as file:

            # record metadata to file in a way that is easy to index
            file.write(f"Arxiv_ID: {entry.id}\n")
            file.write(f"Published: {entry.published}\n")
            file.write(f"Updated: {entry.updated}\n")
            file.write(f"Title: {entry.title}\n")

            try:
                file.write(f"Authors: {', '.join(author.name for author in entry.authors)}\n")
            except AttributeError:
                file.write(f"Authors: None\n")

            try:
                doi = entry.arxiv_doi
            except AttributeError:
                doi = 'None'
            file.write(f"DOI: {doi}\n")

            # Get the links to the abs page and PDF for this e-print
            for link in entry.links:
                if link.rel == 'alternate':
                    file.write(f"Abstract_Link: {link.href}\n")
                elif link.title == 'pdf':
                    file.write(f"PDF_Link: {link.href}\n")     

            try:
                journal_ref = entry.arxiv_journal_ref # journal reference to arxiv paper
            except AttributeError:
                journal_ref = 'None'
            file.write(f"Journal_Ref: {journal_ref}\n")

            try:
                comment = entry.arxiv_comment # author's comment
            except AttributeError:
                comment = 'None'
            file.write(f"Comments: {comment}\n")

            file.write(f"Primary Category: {entry.tags[0]['term']}\n")
            all_categories = [t['term'] for t in entry.tags]
            file.write(f"All Categories: {', '.join(all_categories)}\n")

            file.write(f"Abstract: {entry.summary}\n")
            file.close()

    logging.info(f"Progress: {min(start, end)}/{end} papers.")
    time.sleep(request_delay)