Web Based Interface and Helper Scripts for Clowder
=================================================

info.php
--------

Retrieve clowder status info stored in local mongo instance. Prints returned info to screen.
	
Usage:

		> wget http://localhost/info.php?servers=true&extractors=true&inputs=true&requests=true&headings=false

extract.php
-----------

Pass a file to the clowder extraction bus for metadata extraction.  Prints returned JSON to the screen.
	
Usage:
	
		> wget http://localhost/extract.php?url=http://foo.com/bar.jpg
