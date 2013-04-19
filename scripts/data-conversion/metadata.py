# -*- coding: utf-8 -*-
"""
Created on Tue Jan 29 21:09:09 2013

@author: ran110
@author: rmg599

"""
import glob, os, fnmatch, uuid, netCDF4, logging, csv, xml.sax.saxutils, re, traceback

formatter = logging.Formatter('%(asctime)s %(message)s')
errlogger = logging.getLogger('metadata_bad')
errloggerHdlr = logging.FileHandler('metadata.bad')
errloggerHdlr.setFormatter(formatter)
errlogger.addHandler(errloggerHdlr)
errlogger.setLevel(logging.WARNING)

def walklevel(some_dir, level=1):
    some_dir = some_dir.rstrip(os.path.sep)
    assert os.path.isdir(some_dir)
    num_sep = some_dir.count(os.path.sep)
    for root, dirs, files in os.walk(some_dir):
        yield root, dirs, files
        num_sep_this = root.count(os.path.sep)
        if num_sep + level <= num_sep_this:
            del dirs[:]

def findFiles(starting_dir='.', pattern='*'):
    '''look for files to process'''
    matches = []
    for root, dirnames, filenames in walklevel(starting_dir, 1):
        for filename in fnmatch.filter(filenames, pattern):
            matches.append(os.path.join(root, filename))
    return matches

# Keyed by survey ID
additionalSurveyMetadata = {}
# Keyed by filename
additionalDatasetMetadata = {}

templateFiles = {'child': {
                    'filename':'/projects/r17/scripts/iso19139-child-template.xml',
                    'abstract':'{ABSTRACT}',
                    'uuid':'{UUID}',
                    'parent':'{PARENTUUID}',
                    'title':'{TITLE}',
                    'date':'{DATE}',
                    'theme':'{THEME}',
                    'west':'{WEST}',
                    'east':'{EAST}',
                    'north':'{NORTH}',
                    'south':'{SOUTH}',
                    'wms-address':'{ONLINEWMSRESOURCE}',
                    'wcs-address':'{ONLINEWCSRESOURCE}',
                    'www-address':'{ONLINEWWWRESOURCE}',
                    'layername':'{LAYERNAME}',
                    },
                 'parent': {
                    'filename':'/projects/r17/scripts/iso19139-parent-template.xml',
                    'parent':'{PARENTUUID}',
                    'title':'{TITLE}',
                    'date':'{DATE}',
                    'abstract':'{ABSTRACT}',
                    'theme':'{THEME}',
                    'west':'{WEST}',
                    'east':'{EAST}',
                    'north':'{NORTH}',
                    'south':'{SOUTH}',
                    }
                }

wmsServer="http://siss2.anu.edu.au/thredds/wms/ga/projects/"
wcsServer="http://siss2.anu.edu.au/thredds/wcs/ga/projects/"

def isNumber(s):
    try:
        float(s)
        return True
    except ValueError:
        return False

'''
This creates a parent metadata record if it doesn't already exist. If the file already exists, then this function
will just read out its UUID

returns the UUID of the newly created (or existing) parent metadata record

'''
def createParentMetadataRecord(surveyId, datasetFileName, mydata):
    
    parentfile = '/projects/r17/shared/metadata/P' + surveyId + '.xml'
    if os.path.exists(parentfile):
        f = open(parentfile, 'r')
        contents = f.read()
        f.close()
        return re.search('<gmd:fileIdentifier xmlns:gmx="http://www.isotc211.org/2005/gmx"><gco:CharacterString>(.*)</gco:CharacterString>', contents).group(1)
    else:
        surveyMetadata = None
        if additionalSurveyMetadata.has_key(surveyId):
            surveyMetadata =  additionalSurveyMetadata[surveyId]
    
        fileuuid=uuid.uuid4()
        
        outputTemplate = open(templateFiles['parent']['filename'], 'r').read()
        replaceVars = templateFiles['parent']
        
        #FileIdentifier
        outputTemplate = outputTemplate.replace(replaceVars['parent'], str(fileuuid))
        
        #Title
        title = mydata['filename']
        if  mydata.has_key('title'):
            title = mydata['title']
        if surveyMetadata is not None:
            title = surveyMetadata['title']
        title = xml.sax.saxutils.escape(title)
        outputTemplate = outputTemplate.replace(replaceVars['title'], title)
        
        #Date
        date = 'unknown'
        if surveyMetadata is not None:
            date = surveyMetadata['Beginning Time']
        elif mydata.has_key('date'):
            date = mydata['date'].split('_')[0]
        date = xml.sax.saxutils.escape(date)
        outputTemplate = outputTemplate.replace(replaceVars['date'], date)
        
        #Abstract
        abstract = str(mydata)
        if surveyMetadata is not None:
            abstract = surveyMetadata['abstract']
        abstract = xml.sax.saxutils.escape(abstract)
        outputTemplate = outputTemplate.replace(replaceVars['abstract'], abstract)
        
        #Keywords
        keywordList = []
        if surveyMetadata is not None:
            keywordList += re.findall(r"[\w']+", surveyMetadata['surveyType'])  
            keywordList += re.findall(r"[\w']+", surveyMetadata['dataType'])
        if not keywordList:
            keywordList = [mydata['theme']] # If we have no overrides, use the read theme
        if len(keywordList) < 5:
            keywordList += ['']*(5-len(keywordList)) # If we have less than 5 entries, pad it out to empty strings
        for x in range(0,5):
            key = '{KEYWORD%s}' % (x + 1)
            value = xml.sax.saxutils.escape(keywordList[x])
            outputTemplate = outputTemplate.replace(key, value)
        
        #GeographicBoundingBox
        north = mydata['north']
        south = mydata['south']
        east = mydata['east']
        west = mydata['west']
        if surveyMetadata is not None:
            if isNumber(surveyMetadata['northBoundLatitude']):
                north = surveyMetadata['northBoundLatitude']
            if isNumber(surveyMetadata['southBoundLatitude']):
                south = surveyMetadata['southBoundLatitude']
            if isNumber(surveyMetadata['eastBoundLongitude']):
                east = surveyMetadata['eastBoundLongitude']    
            if isNumber(surveyMetadata['westBoundLongitude']):
                west = surveyMetadata['westBoundLongitude']        
        
        if not isNumber(north):
            north = '90'
        if not isNumber(south):
            south = '-90'
        if not isNumber(east):
            east = '180'
        if not isNumber(west):
            west = '-180'
        outputTemplate = outputTemplate.replace(replaceVars['north'], xml.sax.saxutils.escape(north))
        outputTemplate = outputTemplate.replace(replaceVars['west'], xml.sax.saxutils.escape(west))
        outputTemplate = outputTemplate.replace(replaceVars['south'], xml.sax.saxutils.escape(south))
        outputTemplate = outputTemplate.replace(replaceVars['east'], xml.sax.saxutils.escape(east))
        
        f = open(parentfile, 'w')
        f.write(outputTemplate)
        f.close()
        
        return fileuuid

def createChildMetadataRecord(surveyId, currentFileBasePath, parentUUID, mydata):
    """This creates a child metadata record"""
    fileuuid=uuid.uuid4()
    
    datasetMetadata = None
    if additionalDatasetMetadata.has_key(currentFileBasePath): 
        datasetMetadata = additionalDatasetMetadata[currentFileBasePath]
    
    outputTemplate = open(templateFiles['child']['filename'], 'r').read()
    replaceVars = templateFiles['child']
    
    #FileIdentifier
    outputTemplate = outputTemplate.replace(replaceVars['uuid'], str(fileuuid))
    
    #ParentIdentifier
    outputTemplate = outputTemplate.replace(replaceVars['parent'], str(parentUUID))   
    
    #Title
    title = mydata['filename']
    if datasetMetadata is not None:
        title = datasetMetadata['LABEL'].replace('_', ' ')
    elif mydata.has_key('label'):
        title = mydata['label'].replace('_', ' ')
    outputTemplate = outputTemplate.replace(replaceVars['title'], xml.sax.saxutils.escape(title))
    
    #Abstract
    outputTemplate = outputTemplate.replace(replaceVars['abstract'], xml.sax.saxutils.escape(str(mydata)))
    
    #Date
    date = 'unknown'
    if datasetMetadata is not None:
        date = datasetMetadata['DATE'].split('_')[0]
    elif mydata.has_key('date'):
        date = mydata['date'].split('_')[0]
    outputTemplate = outputTemplate.replace(replaceVars['date'], date)
    
    #Keywords
    keywordList = []
    if datasetMetadata is not None:
        keywordList += [datasetMetadata['Theme']]
        keywordList += [datasetMetadata['Datatype']]
    if not keywordList and mydata.has_key('theme'):
        keywordList = [mydata['theme']] # If we have no overrides, use the read theme
    if len(keywordList) < 5:
        keywordList += ['']*(5-len(keywordList)) # If we have less than 5 entries, pad it out to empty strings
    for x in range(0,5):
        key = '{KEYWORD%s}' % (x + 1)
        value = xml.sax.saxutils.escape(keywordList[x])
        outputTemplate = outputTemplate.replace(key, value)
    
    wwwAddress = ''
    if datasetMetadata is not None:
        wwwAddress = datasetMetadata['URL'] 
    outputTemplate = outputTemplate.replace(replaceVars['wcs-address'], wcsServer+mydata['filename']+'.nc4')
    outputTemplate = outputTemplate.replace(replaceVars['wms-address'], wmsServer+mydata['filename']+'.nc4')
    outputTemplate = outputTemplate.replace(replaceVars['www-address'], wwwAddress + '.html')
    outputTemplate = outputTemplate.replace(replaceVars['layername'], mydata['filename'])
    
    #GeographicBoundingBox
    outputTemplate = outputTemplate.replace(replaceVars['north'], mydata['north'])
    outputTemplate = outputTemplate.replace(replaceVars['west'], mydata['west'])
    outputTemplate = outputTemplate.replace(replaceVars['south'], mydata['south'])
    outputTemplate = outputTemplate.replace(replaceVars['east'], mydata['east'])
    
    f = open('/projects/r17/shared/metadata/' + mydata['filename'] + '.xml', 'w')
    f.write(outputTemplate)
    f.close()
    
    return

curSurveyId=""
parentUUID=""

def _glob(path, *exts):
    """Glob for multiple file extensions

    Parameters
    ----------
    path : str
        A file name without extension, or directory name
    exts : tuple
        File extensions to glob for

    Returns
    -------
    files : list
        list of files matching extensions in exts in path

    """
    path = os.path.join(path, "*") 
    if not os.path.isdir(path):
        path + "*"
    return [f for files in [glob.glob(path + ext) for ext in exts] for f in files]


# Parse the supporting metadata dictionaries. These provide 'override' information for various metadata fields keyed by survey id filename.
print 'Parsing overriding metadata csv files'
surveycsvfile = open('/projects/r17/scripts/metadata/surveys.csv', 'rb')
surveycsvReader = csv.DictReader(surveycsvfile)
for row in surveycsvReader:
    additionalSurveyMetadata[row['surveyId']] = row
surveycsvfile.close()

datasetcsvfile = open('/projects/r17/scripts/metadata/datasets.csv', 'rb')
datasetcsvReader = csv.DictReader(datasetcsvfile)
for row in datasetcsvReader:
    additionalDatasetMetadata[row['Filename']] = row
datasetcsvfile.close()


# Parse the data files
print 'Parsing data files'
fileset = _glob('/projects/r17/GA/', '*demg.isi', '*tmig.isi', '*doseg.isi', '*pctkg.isi', '*ppmthg.isi', '*ppmug.isi')

for currentfile in sorted(fileset):
    try:
        currentFileBasePath = os.path.basename(currentfile)
        filename, extension = os.path.splitext(currentFileBasePath)
        if '.ers.' not in filename and os.path.exists('/projects/r17/shared/netCDF/'+filename+'.nc4'):
            '''
                Harvest currentfile's metadata into a "mydata" hashtable
            '''
            mydata={}
            f=open(currentfile, 'r')
            for line in f.readlines():
                if '=' in line:
                    mydata[line.split('=')[0].strip().lower()] = line.split('=')[1].strip().replace('"','')
            f.close()
            mydata['filename'] = filename
            
            # We need to figure out what survey id(s) this record belongs to. There's a few places we can look
            surveyIdDict = {}
            
            # My data may have a survey ID
            if "surveyid" in mydata:
                surveyIds = mydata.get("surveyid").split('_')
                for surveyId in surveyIds:
                    if surveyId != "" and surveyId.lower() != "unknown":
                        surveyIdDict[surveyId] = True
            
            # Lookup survey IDs from our CSV database
            if currentFileBasePath in additionalDatasetMetadata:
                surveyIds = additionalDatasetMetadata[currentFileBasePath]['SurveyID'].split('_')
                for surveyId in surveyIds:
                    if surveyId != "" and surveyId.lower() != "unknown":
                        surveyIdDict[surveyId] = True
            
            '''
            Harvest geographic bounding box coordinates from currentfile's 
            corresponding netCDF file and store them in "mydata" hastable
            ''' 
            data = netCDF4.Dataset('/projects/r17/shared/netCDF/'+filename+'.nc4','r', format='NETCDF4')
            mydata['north'] = str(data.variables['lat'][-1:][0])
            mydata['west'] = str(data.variables['lon'][0])
            mydata['south'] = str(data.variables['lat'][0])
            mydata['east'] = str(data.variables['lon'][-1:][0])
            data.close()
            
            # We need to duplicate the child for each and every parent (unfortunately)
            if not surveyIdDict:
                errlogger.error(currentfile + ' - skipped as surveyid does not exist')
            
            for surveyId in surveyIdDict:
                parentUUID = createParentMetadataRecord(surveyId, currentFileBasePath, mydata)
                createChildMetadataRecord(surveyId, currentFileBasePath, parentUUID, mydata)
                    
    except Exception, e:
        print 'Exception:', e
        print traceback.format_exc()
        pass
