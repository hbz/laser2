#!/usr/bin/groovy

// @GrabResolver(name='es', root='https://oss.sonatype.org/content/repositories/releases')
@Grapes([
  @Grab(group='net.sf.opencsv', module='opencsv', version='2.0'),
  @Grab(group='com.gmongo', module='gmongo', version='0.9.2'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.0'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.0'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2')
])


import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*
import org.apache.log4j.*
import au.com.bytecode.opencsv.CSVReader
import java.text.SimpleDateFormat


def starttime = System.currentTimeMillis();
def possible_date_formats = [
  new SimpleDateFormat('yyyy/MM/dd'),
  new SimpleDateFormat('dd/MM/yy'),
  new SimpleDateFormat('dd/MM/yyyy'),
  new SimpleDateFormat('yyyy/MM'),
  new SimpleDateFormat('yyyy')
];


// Setup mongo
def options = new com.mongodb.MongoOptions()
options.socketKeepAlive = true
options.autoConnectRetry = true
options.slaveOk = true
def mongo = new com.gmongo.GMongo('127.0.0.1', options);
def db = mongo.getDB('kbplus_ds_reconciliation')

if ( db == null ) {
  println("Failed to configure db.. abort");
  system.exit(1);
}


def ukfam = null

def badfile = new File("${args[0]}-BAD");

println("Loading uk federation data...");
// Load the fam reconcilliation data
def target_service = new RESTClient('http://metadata.ukfederation.org.uk')

try {
  target_service.request(GET, ContentType.XML) { request ->
    uri.path='/ukfederation-metadata.xml'
    response.success = { resp, data ->
      // data is the xml document
      ukfam = data;
    }
    response.failure = { resp ->
      println("Error - ${resp.status}");
      System.out << resp
    }
  }
}
catch ( Exception e ) {
  e.printStackTrace();
}

println("Loaded uk federation data... ${ukfam.name()}");

if ( !ukfam.name().equals('EntitiesDescriptor')) {
  println("Failed to load latest UK FAM Data. exiting");
  system.exit(1);
}

// To clear down the gaz: curl -XDELETE 'http://localhost:9200/gaz'
// CSVReader r = new CSVReader( new InputStreamReader(getClass().classLoader.getResourceAsStream("./IEEE_IEEEIEL_2012_2012.csv")))
println("Processing ${args[0]}");
CSVReader r = new CSVReader( new InputStreamReader(new FileInputStream(args[0]),java.nio.charset.Charset.forName('UTF-8')) )

def bad_rows = []

String [] nl;

String [] so_header_line = r.readNext()

println("Read column headings: ${so_header_line}");

int rownum = 0;
def stats = [:]
stats.skipped = 0;
stats.added = 0;
stats.bad = 0;
stats.new = 0;
stats.existing = 0;
stats.total = 0;

while ((nl = r.readNext()) != null) {
  // institutional_name,ringold_id,ingenta_id,jc_id,ip_range,ukfamf_idp,athens_id,sector_name
  def reason = ""
  rownum++
  stats.total++
  boolean bad = false;
  String badreason = null;

  if ( ( nl[0] != null ) && ( nl[0].trim().length() > 0 ) ) {
    if ( nl[7] == 'Higher Education' ) {
      def org = db.orgs.findOne(ukfam:nl[5])
      if ( org==null ) {
        org = [:]
        stats.new++
      }
      else {
        stats.existing++
      }
      org.name = nl[0];
      org.normName = nl[0].trim().toLowerCase()
      org.ringoldId = nl[1];
      org.ingentaId = nl[2];
      org.jcId = nl[3];
      org.ipRange = nl[4];
      org.ukfam = nl[5];
      org.athensId = nl[6];
      org.sectorName = nl[7];
      org.lastmod = System.currentTimeMillis();

      // org.famId = resolveFAM(ukfam,nl[5])
      resolveFAM(ukfam,nl[5], org)
      // Find from ukfam, @entityID==nl[5]
      db.orgs.save(org);
      stats.added++
      if ( ! org.famId ) {
        println("Unable to locate node for ${nl[5]}");
      }
    }
    else {
      println("Skipping non-HE org");
      stats.skipped++;
    }
  }
  else {
    println("No name for row ${rownum}");
    badfile << "${nl[0]},${nl[1]},${nl[2]},${nl[3]},${nl[4]},${nl[5]},${nl[6]},${nl[7]},\"No name for row ${rownum}\"\n"
    stats.bad++
  }
}


println("${stats}");

def statsfile = new File("stats.txt");
statsfile << "${new Date().toString()}\n\nOrgs import\n-----------\n\n"
stats.each { stat ->
  statsfile << "${stat.key} : ${stat.value}\n"
}

def resolveFAM(xmldoc, code, org) {

  def codes = code.split(';');
  def result = null;

  for ( ci = codes.iterator(); (ci.hasNext() && result==null); ) {
    def c = ci.next().trim();
    def famnode = xmldoc.EntityDescriptor.findAll { it.@entityID == c }
    if ( famnode.size() > 0 ) {
      // result=famnode[0].@ID.text();
      org.famId = famnode[0].@ID.text();
      org.scope = famnode[0].Extensions?.Scope?.text()
      println("scope: ${org.scope}");
    }
    else {
    }
  }

  result
}
