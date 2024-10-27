# This crawler fetches from the OAI server which ArXiv prefers we use for "Bulk Downloading".
#     More on OAI-PMH: https://info.arxiv.org/help/oa/index.html
#     OAI returns a set amount of up to 1000 records per request.
# The script creates an arxivPapers folder and creates a .txt file for each record listing the metadata.
#      It uses arXiv metadata format (similar to their API).
#      The arXiv metadata captured includes: ID, title, authors, creation date, categories, abstract.
# Here is an example of the XML data in arXiv format:
#     http://export.arxiv.org/oai2?verb=GetRecord&identifier=oai:arXiv.org:0804.2273&metadataPrefix=arXiv

import xml.etree.ElementTree as ET
import requests
import os
import time
import logging

cur_papers = 0   
max_papers = 10000  # Specify how many to retrieve

# Some basic logging to help ensure the server requests are correct
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
log_filename = 'arxiv_fetch.log'
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
)

papersFolder = "arxivPapers"
os.makedirs(papersFolder,exist_ok=True)

# These settings are to help avoid triggering any kind of DDoS protection (from 429 or 503 "retry-after" requests)
request_delay = 3   # They seem to want a 3 second delay between requests
backoff_factor = 2  # Basic exponential backoff 
max_retries = 3     # For non 503 errors

resumption_token = None # Resumption token tells us whether more records are still available.
#     Explained more here: https://www.openarchives.org/OAI/2.0/openarchivesprotocol.htm#FlowControl

base_url = 'http://export.arxiv.org/oai2' # OAI-PMH base URL for arXiv

while cur_papers < max_papers:

    if resumption_token: # Once we have resumption token, can't specify other parameters
        params = {
            'verb': 'ListRecords',
            'resumptionToken': resumption_token,
        }
    else:
        params = {
            'verb': 'ListRecords',
            'metadataPrefix': 'arXiv',
            'set': 'cs',  # Category Computer Science, no sub-categories with OAI.
        }

    try:
        for attempt in range(max_retries):
            logging.info(f"Initiating request.")
            response = requests.get(base_url, params=params)

            # Handle specific status codes
            if response.status_code == 200: # Request acknowledged.
                break  
            elif response.status_code == [429, 503]: # This is to avoid their server interpreting our requests as a DDoS attack.
                retry_after = int(response.headers.get("Retry-After", request_delay))
                logging.warning(f"429 Too Many Requests. Retrying after {retry_after} seconds...")
                time.sleep(retry_after)
            elif 500 <= response.status_code < 600: # Basic exponential backoff to avoid spamming them.
                logging.warning(f"Server error {response.status_code}. Retrying...")
                time.sleep(request_delay * (backoff_factor ** attempt))
            else:
                logging.error(f"Unexpected status code {response.status_code}. Stopping requests.")
                break
        else:
            logging.error("Max retries reached. Stopping requests.")
            break
        
        root = ET.fromstring(response.content) # Our request is returned as an XML document
        #logging.info("XML response parsed.")

    except requests.exceptions.RequestException as e:
        logging.error(f"Request failed: {e}")
        break
    except ET.ParseError as e:
        logging.error(f"Failed to parse XML: {e}")
        break

    ns = {
        'oai': 'http://www.openarchives.org/OAI/2.0/', # Identify the two primary namespaces 
        'arxiv': 'http://arxiv.org/OAI/arXiv/'
    }

    try:
        records = root.findall('.//oai:record', ns)
        # logging.info(f"{len(records)} records obtained.")

        for record in records: # They can return up to 200 (1000?) records per request
            if cur_papers >= max_papers:
                logging.info("Reached maximum paper limit.")
                break
            cur_papers += 1

            # Parse the OAI information
            header = record.find('oai:header', ns)
            identifier = header.find('oai:identifier', ns).text # this includes the arXiv ID
            datestamp = header.find('oai:datestamp', ns).text # last time the record was modified
            
            # Parse the arXiv information
            # Includes: ID, title, authors, creation date, categories, abstract, PDF links.
            metadata = record.find('oai:metadata', ns)
            if metadata is None:
                logging.error(f"No metadata found for identifier {identifier}. Skipping record.")
                continue  # Skip this record
            arxiv_meta = metadata.find('arxiv:arXiv', ns)
            if arxiv_meta is None:
                logging.error(f"No arxive metadata found for identifier {identifier}. Skipping record.")
                continue  # Skip this record
            
            id_element = arxiv_meta.find('arxiv:id', ns) # arXiv ID
            id = id_element.text.strip() if id_element is not None else "No arxiv ID found (something is wrong)."

            created_element = arxiv_meta.find('arxiv:created', ns) # arXiv creation date
            created = created_element.text.strip() if created_element is not None else "No creation date."

            title_element = arxiv_meta.find('arxiv:title', ns)
            title = title_element.text.strip() if title_element is not None else "No title available."

            authors = []
            authors_element = arxiv_meta.find('arxiv:authors', ns)
            if authors_element is not None:
                for author in authors_element.findall('arxiv:author', ns):
                    keyname = author.find('arxiv:keyname', ns).text if author.find('arxiv:keyname', ns) is not None else ""
                    forenames = author.find('arxiv:forenames', ns).text if author.find('arxiv:forenames', ns) is not None else ""
                    authors.append(f"{forenames} {keyname}".strip())

            categories_element = arxiv_meta.find('arxiv:categories', ns) # sub-categories for CS
            category = categories_element.text.strip() if categories_element is not None else "No categories listed."
            
            abstract_element = arxiv_meta.find('arxiv:abstract', ns)
            abstract = abstract_element.text.strip() if abstract_element is not None else "No abstract available."

            # logging.info(ET.tostring(arxiv_meta, encoding='unicode')) # check the arxiv_meta structure

            '''
            # OAI doesn't support PDF/relevancy links it seems
            links = arxiv_meta.findall('arxiv:link', ns)
            if not links:
                logging.warning(f"No links found for identifier: {identifier}")
            '''

            filename = os.path.join(papersFolder, f"arxiv_{id}.txt")
            with open(filename, 'w') as f:
                f.write(f"OAI_Identifier: {identifier}\n")
                f.write(f"OAI_Datestamp: {datestamp}\n")
                f.write(f"Arxiv_ID: {id}\n")
                f.write(f"Title: {title}\n")
                f.write(f"Created: {created}\n")
                f.write(f"Authors: {', '.join(authors)}\n")
                f.write(f"Abstract: {abstract}\n")

                '''
                # Write all found links with descriptions 
                f.write("Links:\n")
                for link in links:
                    print(link)
                    rel = link.get('rel', 'unknown')
                    href = link.get('href', 'No link available')
                    f.write(f"  - {rel}: {href}\n")
                '''

                f.flush()
                f.close()

    except Exception as e:
        logging.error(f"Error processing records: {e}")
        break

    if cur_papers >= max_papers:
        break

    logging.info(f"Progress: {cur_papers}/{max_papers} papers.")
    
    # We need to check for a resumption token to continue to recieve records
    resumption_token_element = root.find('oai:ListRecords/oai:resumptionToken', ns)
    if resumption_token_element is not None and resumption_token_element.text:
        resumption_token = resumption_token_element.text.strip()
        # logging.info(f"Resumption token found: {resumption_token}")
        time.sleep(request_delay)
    else:
        logging.info("No resumption token found. Ending data fetching.")
        break  


