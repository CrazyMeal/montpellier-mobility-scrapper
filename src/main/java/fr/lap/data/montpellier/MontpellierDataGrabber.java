package fr.lap.data.montpellier;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fr.lap.data.IDataGrabber;
import fr.lap.domain.BasicParkingData;
import fr.lap.domain.City;
import fr.lap.domain.DocumentValidationResult;
import fr.lap.domain.Parking;
import fr.lap.domain.ParkingData;

public class MontpellierDataGrabber implements IDataGrabber {
	
	final static Logger logger = Logger.getLogger(MontpellierDataGrabber.class);

	private List<Document> xmlDocuments;
	
	private Schema schema;
	
	private DocumentValidationResult validationResult;
	
	public void getSources(List<String> urlList) {
		this.xmlDocuments = new ArrayList<Document>();
		try {
			for (String urlString : urlList) {

				if (urlString.contains(".")) {
					String extension = urlString.substring(urlString.lastIndexOf("."));
					URL url = new URL(urlString);
					
					if (extension.equals(".xsd")) {
						HttpURLConnection schemaConn = (HttpURLConnection) url.openConnection();
						InputStream stream = schemaConn.getInputStream();
						
						if (stream.available() > 0) {
							StreamSource streamSource = new StreamSource(stream);
							SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
							this.schema = factory.newSchema(streamSource);
						} else {
							logger.error("Pas de data a lire sur l'url du schema XSD - url> " + urlString);
						}
						
						stream.close();
					} else {
						InputStream stream = url.openStream();
						
						if (stream.available() > 0) {
							DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
							Document document = parser.parse(stream);
							document.normalize();
							this.xmlDocuments.add(document);
						} else {
							logger.error("Pas de data a lire sur l'url du XML - url> " + urlString);
						}
						
						stream.close();
					}
				}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			 e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
	}

	public DocumentValidationResult validateSources() {
		try {
			if (this.schema != null) {
				List<Document> validParkingList = new ArrayList<Document>();
				List<Document> unValidParkingList = new ArrayList<Document>();
				
			    Validator validator = this.schema.newValidator();
			    for (Document doc : this.xmlDocuments) {
			    	try {
						validator.validate(new DOMSource(doc));
						validParkingList.add(doc);
					} catch (SAXException e) {
						e.printStackTrace();
						
						if (unValidParkingList == null) {
							unValidParkingList = new ArrayList<Document>();
						}
						
						unValidParkingList.add(doc);
					}
			    }
			    
			    this.validationResult = new DocumentValidationResult(validParkingList, unValidParkingList);
			    
			} else {
				logger.warn("Pas de source XSD pour valider la structure XML");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return this.validationResult;
	}

	public List<ParkingData> launchSources() {
		List<ParkingData> parkingDataList = new ArrayList<ParkingData>();
		DateFormat parser = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss");
		City montpellierCity = new City("Montpellier");
		
		for (Document document : this.validationResult.getValidDocumentList()) {
			NodeList nList = document.getElementsByTagName("park");
		    
		    for (int id = 0 ; id < nList.getLength() ; id++) {
		    	Node node = nList.item(id);
		    	
		    	if (node.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) node;
					
					String dateFromFile = eElement.getElementsByTagName("DateTime").item(0).getTextContent();
					Date dateOfData = null;
					try {
						dateOfData = parser.parse(dateFromFile);
					} catch (ParseException e) {
						e.printStackTrace();
					}
					
					String name = eElement.getElementsByTagName("Name").item(0).getTextContent();
					String status = eElement.getElementsByTagName("Status").item(0).getTextContent();
					int freePlaces = Integer.valueOf(eElement.getElementsByTagName("Free").item(0).getTextContent());
					int totalPlaces = Integer.valueOf(eElement.getElementsByTagName("Total").item(0).getTextContent());
					
					DateTime nowDateTime = new DateTime();
					
					Parking parking = new Parking(name, montpellierCity, nowDateTime.toDate());
					
					BasicParkingData bpd = new BasicParkingData(freePlaces, totalPlaces, status, dateOfData, parking);
					parkingDataList.add(bpd);
					
				}
		    }
		}
		
		return parkingDataList;
	}

}
