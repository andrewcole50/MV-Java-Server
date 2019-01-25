# MV-Java-Server
Pre-RESTful Java Server

Used this server for years to provide a RESTful like interface for a website to communicate to mvBase. Set up was prior to RESTful standards and uses a simple socket connection to parse JSON to handle functions. This has been tested and works with mvBase but it should work with any d3 system as well. 

## Dependencies
* GSON
* Rocket Java MVSP
* Apache Commons Pool
* MVUtils repo

## Sample Requests
### JREAD
`{
	"command": "JREAD",
	"file": "CUSTOMER",
	"key": "CUST.KEY"
}`

Will return either false (0) or the raw record. Receiving program needs to sanitize @AM, @VM, @SM. There is an untested method to convert a MV object to a JSON array.

### JWRITE
`{
	"command": "JWRITE",
	"file": "CUSTOMER",
	"key": "CUST.KEY~AMattr1~AMattr2~AMattr<3,1>~VMattr<3,1,1>~SMattr<3,1,2>"
}`

Returns boolean

## Things of Note
* Due to encoding issues, I found it easier to send files with @AM = '~AM', @VM = '~VM', and @SM = '~SM'. I'm sure there's a better way.
* Makes use of my repo MVUtils to output formatted data from mvdbms. Uses DICT file to run OCONV on attributes.
* Makes use of a pool of connections to the mvdbms. Starts with a single connection but if a request comes in and all connections are used, a new one will spawn (unless current number of connections are at max) and return to the pool.
