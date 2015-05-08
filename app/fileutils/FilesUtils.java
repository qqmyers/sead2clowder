package fileutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Map;
import play.Logger;
import play.Application;
import play.Play;

import org.json.JSONObject;
import org.json.XML;

import org.apache.commons.io.FileUtils;

/**
 * Utilities for preprocessing of uploaded files.
 * 
 * @author Constantinos Sophocleous
 */
public class FilesUtils {
	
	//Read custom-defined extensions-to-MIME-types associations from config file.
	private static Map appMimetypes = new HashMap();
	static{
		for (String currKey: Play.application().configuration().keys()){
			if(currKey.startsWith("mimetype.")){
				appMimetypes.put(currKey.substring(currKey.indexOf(".")+1), Play.application().configuration().getString(currKey));
			}
		}
	}

	//Get custom MIME type of file based on extension if such is defined.
	public static String getFilePrioritizedType(String filename){
	
		String fileExtension = filename.substring(filename.lastIndexOf(".")+1);
		if(!fileExtension.equals(filename) && appMimetypes.containsKey(fileExtension)) {
			return (String) appMimetypes.get(fileExtension);
		}		
		else return "";		
	}

	public static String getMainFileTypeOfZipFile(File compressedFile, String filename, String containerType){
		
		String mainFileType = "multi/files-zipped";
		
		try {
			if(filename.startsWith("MEDICI2ZIPPED_"))
				return "multi/files-zipped";
			if(filename.startsWith("MEDICI2MULTISPECTRAL_"))
				return "imageset/zipped_multispectral";
			
			ZipFile zipFile = new ZipFile(compressedFile);			
			Enumeration zipEntries = zipFile.entries();
			
			String allPTMsFlag = "notfound";
			
            while (zipEntries.hasMoreElements()) {
            	ZipEntry currEntry = ((ZipEntry)zipEntries.nextElement());
            	if(!currEntry.isDirectory()){
	                String fileName = currEntry.getName(); 
	                if(fileName.toLowerCase().endsWith(".x3d")){
	                	zipFile.close();
	                	mainFileType = "model/x3d-zipped";
	                	return mainFileType;
	                }
	                if(fileName.toLowerCase().endsWith(".obj")){
	                	zipFile.close();
	                	mainFileType = "model/obj-zipped";
	                	return mainFileType;
	                }
	                if(fileName.toLowerCase().endsWith(".lp")){
	                	zipFile.close();
	                	mainFileType = "imageset/ptmimages-zipped";
	                	return mainFileType;
	                }
	                if(fileName.toLowerCase().endsWith(".sfmdataset")){
	                	zipFile.close();
	                	mainFileType = "model/sfm-zipped";
	                	return mainFileType;
	                }
	                if(fileName.toLowerCase().contains("__slides")){
	                	zipFile.close();
	                	mainFileType = "multi/video-presentation-zipped";
	                	return mainFileType;
	                }
	                if(fileName.toLowerCase().endsWith(".ptm") && allPTMsFlag.equals("notfound")){
	                	allPTMsFlag = "found";
	                }
	                else if(!fileName.toLowerCase().endsWith(".ptm")){
	                	allPTMsFlag = "noptm";
	                }                
            	}
            }
            zipFile.close();
            
            if(allPTMsFlag.equals("found"))
            	return "multi/files-ptm-zipped";
            
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return ("ERROR: " + e.getMessage());
		}		
		return mainFileType;
	}
	
	public static String readXMLgetJSON(File xmlFile){
		
		try{
			FileInputStream fis = new FileInputStream(xmlFile);
			byte[] data = new byte[(int)xmlFile.length()];
			fis.read(data);
		    fis.close();
		    String theXML = new String(data, "UTF-8");
		    
		  //Remove spaces from XML tags
//		    int currStart = theXML.indexOf("<");
//		    int currEnd = -1;
//		    String xmlNoSpaces = "";		    
//		    while(currStart != -1){
//		      xmlNoSpaces = xmlNoSpaces + theXML.substring(currEnd+1,currStart);
//		      currEnd = theXML.indexOf(">", currStart+1);
//		      xmlNoSpaces = xmlNoSpaces + theXML.substring(currStart,currEnd+1).replaceAll(" ", "_");
//		      currStart = theXML.indexOf("<", currEnd+1);
//		    }
//		    xmlNoSpaces = xmlNoSpaces + theXML.substring(currEnd+1);
//		    theXML = xmlNoSpaces;
		    
		    Logger.debug("thexml: " + theXML);
		    
		    JSONObject xmlJSONObj = XML.toJSONObject(theXML);
		    
		    return xmlJSONObj.toString();
			
		}catch (Exception e) {
			// TODO Auto-generated catch block
			return ("ERROR: " + e.getMessage());
		}
	}

}
