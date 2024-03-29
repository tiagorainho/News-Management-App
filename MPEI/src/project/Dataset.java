package project;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class Dataset {
	private ArrayList<Publication> dataset;
	private int maxValues;
	private int errors;
	private int excluded;
	private BloomFilterOptimized titlesBloomFilter;
	private BloomFilterOptimized authorsBloomFilter;
	private BloomFilterIncremental titlesBloomFilterIncremental;
	private boolean onlyTrustTrustedEntities;
	private static final String[] trustedEntities = {"New York Times", "Breitbart", "CNN", "Business Insider", "Atlantic", "Fox News", "Talking Points Memo", "Buzzfeed News", "National Review", "Guardian", "New York Post", "NPR", "Reuters", "Vox", "Washington Post"};   
	private static final String[] trustedEntitiesMark = {" - The New York Times", ",Atlantic", ",Guardian", " - Breitbart"}; // etc
	
	public Dataset(int numValuesAprox, boolean onlyTrustTrustedEntities) {
		this.onlyTrustTrustedEntities = onlyTrustTrustedEntities;
		dataset = new ArrayList<Publication>();
		this.maxValues = 0;
		this.excluded = 0;
		this.errors = 0;
		this.titlesBloomFilter = new BloomFilterOptimized(numValuesAprox, 4);
		this.authorsBloomFilter = new BloomFilterOptimized(numValuesAprox, 4);
		this.titlesBloomFilterIncremental = new BloomFilterIncremental(numValuesAprox, 4);
	}
	
	public Dataset(int numValuesAprox) {
		this(numValuesAprox, false);
	}
	
	public void showSimilarNews(double threshHold, int permutations) {
		showSimilarNews(threshHold, permutations, 10, true);
	}
	
	public void showSameTitleSimilarContent(double threshHold) {
		showSameTitleSimilarContent(threshHold, 100, 10);
	}
	
	public void showSameTitlePublications() {
		List<Publication> publications = getPublicationsWithEqualTitles();
		for(Publication p: publications) {
			System.out.println(p.getContent().toString() + "\n");
		}
	}
	
	public void showSameTitleSimilarContent(double threshHold, int permutations, int shingleLen) {
		List<Publication> publications = getPublicationsWithEqualTitles();
		LinkedList<String> news = new LinkedList<String>();
		for(Publication p: publications) {
			news.add(p.getContent());
		}
		List<LinkedList<Integer>> resp = getSameTitleSimilarContent(publications, threshHold, permutations, shingleLen);
		for(int i=0;i<resp.size();i++) {
			LinkedList<Integer> llAux = resp.get(i);
			for(int j=0;j<llAux.size();j++) {
				System.out.printf("%d - %s\n",i, news.get(llAux.get(j)));
			}
		}
		if(resp.size() == 0) {
			System.out.println("No match");
		}
	}
	
	public List<LinkedList<Integer>> getSameTitleSimilarContent(List<Publication> publications, double threshHold, int permutations, int shingleLen) {
		List<String> news = new LinkedList<String>();
		List<String> titles = new LinkedList<String>();
		for(Publication p: publications) {
			news.add(p.getContent());
			titles.add(p.getTitle());
		}
		
		MinHash minHash = new MinHash(permutations);
		minHash.setThreshHold(threshHold);
		minHash.add(news);
		List<LinkedList<Integer>> resp = minHash.getSimilaresWithConstantField(news, titles);
		registLog(resp, news, "news.txt");
		registLog(resp, titles, "titles.txt");
		return resp;
	}
	
	public List<LinkedList<Integer>> getSameTitleSimilarContent(double threshHold, int permutations, int shingleLen) {
		return getSameTitleSimilarContent(getPublicationsWithEqualTitles(), threshHold, permutations, shingleLen);
	}
	
	public List<LinkedList<Integer>> getSameTitleSimilarContent(double threshHold) {
		return getSameTitleSimilarContent(threshHold, 100, 10);
	}
	
	public void showPublicationsWithEqualTitles() {
		List<Publication> list = getPublicationsWithEqualTitles();		
		for(Publication p: list) {
			System.out.println(p.toString());
		}
		if(list.size() == 0) {
			System.out.println("No match found");
		}
	}
	
	private List<Publication> getPublicationsWithEqualTitles(){
		List<Publication> aux = new LinkedList<Publication>();
		List<Publication> list = new LinkedList<Publication>();
		for(int i=0;i<this.dataset.size();i++) {
			if(this.titlesBloomFilterIncremental.containsMoreThanOne(this.dataset.get(i).getTitle())) {
				aux.add(this.dataset.get(i));
			}
		}
		// clean up false positives
		for(int i=0;i<aux.size();i++) {
			int count = 0;
			for(int j=0;j<aux.size();j++) {
				if(aux.get(i).getTitle().equals(aux.get(j).getTitle())) {
					count++;
				}
			}
			if(count > 1) {
				list.add(aux.get(i));
			}
		}
		return list;
	}
	
	public void showSimilarNews(double threshHold, int permutations, int shingleLen, boolean showProgress) {
		System.out.println("Preparing shingles for Min Hashing...");
		MinHash minHash = new MinHash(permutations, shingleLen);
		minHash.setThreshHold(threshHold);
		long start = System.currentTimeMillis();
		List<String> news = getPublicationsContent();
		minHash.add(news);
		long durationMiliseconds = System.currentTimeMillis() - start;
		float durationSeconds = durationMiliseconds/ (float) 1000;
		System.out.printf("Min Hash preparation finished in %.3f seconds\n",durationSeconds);
		System.out.println("Calculating Min Hash...");
		start = System.currentTimeMillis();
		List<LinkedList<Integer>> list = minHash.getSimilars(showProgress);
		int count = 0;
		for(int i=0;i<list.size();i++) {
			LinkedList<Integer> llAux = list.get(i);
			for(int j=0;j<llAux.size();j++) {
				System.out.printf("%d - %s\n",i, news.get(llAux.get(j)));
				count++;
			}
		}
		durationMiliseconds = System.currentTimeMillis() - start;
		durationSeconds = durationMiliseconds/ (float) 1000;
		registLog(list, news , "news.txt");
		System.out.printf("Min Hash finished in %.3f seconds, %d combinations found and %d news are at least %.2f similar\n",durationSeconds, list.size(), count, threshHold);
	}
	
	private int getLength() {
		int len = this.dataset.size();
		if(this.maxValues != 0) {
			if(this.dataset.size() > this.maxValues) {
				len = this.maxValues;
			}
		}
		return len;
	}
	
	public void showSimilarTitles(double threshHold, int permutations) throws IOException {
		showSimilarTitles(false, threshHold, permutations, true);
	}
	
	public void showSimilarTitles(double threshHold, int permutations, boolean showProgress) throws IOException {
		showSimilarTitles(false, threshHold, permutations, showProgress);
	}
	
	public void showSimilarTitles(boolean acceptRepetition, double threshHold, int permutations) throws IOException {
		showSimilarTitles(acceptRepetition, threshHold, permutations, true);
	}
	
	public void showSimilarTitles(boolean acceptRepetition, double threshHold, int permutations, boolean showProgress) throws IOException {
		System.out.println("Preparing for Min Hashing...");
		MinHash minHash = new MinHash(permutations);
		minHash.setThreshHold(threshHold);
		List<String> titles = getTitles(!acceptRepetition);
		titles = purifyTitles(titles);
		minHash.add(titles);
		System.out.println("Calculating Min Hash...");
		long start = System.currentTimeMillis();
		List<LinkedList<Integer>> list = minHash.getSimilars(showProgress);
		int count = 0;
		for(int i=0;i<list.size();i++) {
			LinkedList<Integer> llAux = list.get(i);
			for(int j=0;j<llAux.size();j++) {
				System.out.printf("%d - %s\n",i, titles.get(llAux.get(j)));
				count++;
			}
		}
		long durationMiliseconds = System.currentTimeMillis() - start;
		float durationSeconds = durationMiliseconds/ (float) 1000;		
		System.out.printf("Min Hash finished in %.3f seconds, %d combinations found and %d titles are at least %.2f similar\n",durationSeconds, list.size(), count, threshHold);
		System.out.println("Writing to log file");
		registLog(list, titles, "titles.txt");
	}
	
	public void showSimilarTitles() throws IOException {
		showSimilarTitles(0.5, 100);
	}
	
	public void showSimilarTitles(double threshHold) throws IOException {
		showSimilarTitles(threshHold, 100);
	}
	
	private List<String> purifyTitles(List<String> list){
		List<String> newList = new LinkedList<String>();
		for(String str: list) {
			for(String entity: this.trustedEntitiesMark) {
				str = str.replace(entity, "");
			}
			newList.add(str);
		}
		return newList;
	}
	
	private void registLog(List<LinkedList<Integer>> links, List<String> list, String fileName) {
		String sep = "/";
		if(System.getProperty("os.name").contains("Windows")) {
			sep = "\\";
		}
		fileName = "logs" + sep + fileName;
		
		if(links.size() == 0) {
			System.out.println("No information to save on log file");
			return;
		}
		File f = new File(fileName);
		if(!f.exists()) {
			String dir = f.getAbsolutePath().substring(0,f.getAbsolutePath().lastIndexOf("/"));
			File dirs = new File(dir);
			if(dirs.mkdirs()) {
				System.out.println("Error creating log file");
				return;
			}
			f = new File(fileName);
		}
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileWriter(f), false);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		for(int i=0;i<links.size();i++) {
			LinkedList<Integer> llAux = links.get(i);
			for(int j=0;j<llAux.size();j++) {
				pw.write(i + " - " + list.get(llAux.get(j)) + "\n\n");
			}
			pw.write("\n\n");
			pw.write("########################################################################################################################\n\n");
		}
		System.out.println("Saved on log file: " + f.getAbsolutePath());
		pw.close();
	}
	
	public void addValuesCSV(String fileName) throws FileNotFoundException {
		System.out.println("Reading file...");
		Reader in = new FileReader(fileName);
		System.out.println("File has been successfully read\nParsing data...");
		Iterable<CSVRecord> records = null;
		try {
			records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
		} catch (IOException e) {
			System.out.println("Excell not working");
			e.printStackTrace();
			System.exit(0);
		}
		for (CSVRecord record : records) {
			if(this.maxValues != 0 && this.maxValues <= dataset.size()) {
				break;
			}
			String parts[] = new String[10];
		    parts[0] = record.get("id").trim();
		    parts[2] = record.get("title").trim();
		    parts[3] = record.get("publication").trim();
		    parts[4] = record.get("author").trim();
		    parts[9] = record.get("content").trim();
		    addToDataset(parts);
		}
		System.out.println(getParseInfo());
	}
	
	public void addToDataset(String[] parts) {
		dataset.add(new Publication(this.dataset.size(), parts[4], parts[2], parts[3], parts[9]));
		titlesBloomFilter.add(parts[2]);
		authorsBloomFilter.add(parts[4]);
		titlesBloomFilterIncremental.add(parts[2]);
	}
	
	public void addValuesCSV(File fileName, String sep) {
		System.out.println("Reading file...");
		List<String> news = getLines(fileName);
		System.out.println("File has been successfully read\nParsing data...");
		
		for(int i = 1 ; i < news.size() ; i++) {
			if(this.maxValues != 0 && this.maxValues <= dataset.size()) {
				break;
			}
			String parts[] = news.get(i).split(sep);
			try {
				if(this.onlyTrustTrustedEntities) {
					if(isTrusted(parts[3].trim())) {
						addToDataset(parts, sep);
					}
					else {
						this.excluded++;
					}
				}
				else {
					addToDataset(parts, sep);
				}
			}
			catch(Exception e) {
				this.errors++;
			}
		}
		System.out.println(getParseInfo());
	}
	
	private void addToDataset(String[] parts, String sep) {
		dataset.add(new Publication(this.dataset.size(), parts[4], parts[2], parts[3], getContent(parts,9, sep)));
		titlesBloomFilter.add(parts[2]);
		authorsBloomFilter.add(parts[1]);
	}
	
	public void addToDataset(Publication p) {
		p.setId(this.dataset.size());
		dataset.add(p);
		authorsBloomFilter.add(p.getAuthor());
		titlesBloomFilter.add(p.getTitle());
	}
	
	public String[] getPublicators() {
		ArrayList<String> publicators = new ArrayList<String>();
		for(int i=0;i<this.dataset.size();i++) {
			String publicator = this.dataset.get(i).getPublicator();
			if(!publicators.contains(publicator)) {
				publicators.add(publicator);
			}
		}
		String[] content = new String[publicators.size()];
		for(int i = 0;i<publicators.size();i++) {
			content[i] = publicators.get(i);
		}
		return content;
	}
	
	private boolean isTrusted(String publicator) {
		for(int i=0;i<this.trustedEntities.length;i++) {
			if(publicator.compareTo(this.trustedEntities[i]) == 0) {
				return true;
			}
		}
		return false;
	}
	
	public boolean containsTitle(String title) {
		return this.titlesBloomFilter.contains(title.trim());
	}
	
	public boolean containsTitleIncremental(String title) {
		return this.titlesBloomFilterIncremental.contains(title.trim());
	}
	
	public boolean containsMoreThanOneTitle(String title) {
		return this.titlesBloomFilterIncremental.containsMoreThanOne(title.trim());
	}
	
	public void setMaxValues(int value) {
		this.maxValues = value;
	}
	
	public int getErrors() {
		return this.errors;
	}
	
	private String getParseInfo() {
		if(this.dataset.size() > 0) {
			if(this.errors + this.excluded > 0) {
				return "Data has been parsed but some information provided wasnt correctly formated. " + (this.errors + this.excluded) + " errors";
			}
			else {
				return "Data has been successfully parsed";
			}
		}
		else {
			if(this.errors > 0 && this.excluded == 0) {
				return "Major error parsing, no information was retained";
			}
			else if(this.excluded > 0) {
				return "No information was retained due to trusted entities impositions";
			}
			else {
				return "Error parsing all lines";
			}
		}		
	}
	
	public List<String> getPublicationsContent(int value) {
		Set<String> list = new HashSet<String>();
		String content;
		for(int i=0;i<value;i++) {
			content = this.dataset.get(i).getContent();
			list.add(content);
		}
		List<String> newlist = new LinkedList<String>();
		for(String str: list) {
			newlist.add(str);
		}
		return newlist;
	}
	
	
	public List<String> getPublicationsContent() {
		return getPublicationsContent(this.dataset.size());
	}
 	
	public List<String> getTitles(int value, boolean getUniques) {
		List<String> list = new LinkedList<String>();
		if(getUniques) {
			Set<String> listAux = new HashSet<String>();
			String title;
			for(int i=0;i<value;i++) {
				title = this.dataset.get(i).getTitle();
				//if(title.length() != 0) {
				listAux.add(title);
				//}
			}
			
			for(String str: listAux) {
				list.add(str);
			}
			return list;
		}
		else {
			String title;
			for(int i=0;i<value;i++) {
				title = this.dataset.get(i).getTitle();
				//if(title.length() != 0) {
					list.add(title);
				//}
			}
		}
		return list;
	}
	
	public boolean authorExits(String authorName) {
		if(!this.authorsBloomFilter.contains(authorName)) {
			return false;
		}
		// make sure its the real one
		String authorAux;
		for(int i=0;i<this.dataset.size();i++) {
			authorAux = this.dataset.get(i).getAuthor();
			if(authorAux.equals(authorName)) {
				return true;
			}
		}
		return false;
	}
	
	public List<String> getAuthors() {
		return getAuthors(this.dataset.size());
	}
	
	public List<String> getAuthors(int value) {
		Set<String> listAux = new HashSet<String>();
		String author;
		for(int i=0;i<value;i++) {
			author = this.dataset.get(i).getAuthor();
			if(author.length() != 0) {
				listAux.add(author);
			}
		}
		LinkedList<String> list = new LinkedList<String>();
		for(String str: listAux) {
			list.add(str);
		}
		return list;
	}
	
	public BloomFilterIncremental getBloomFilterIncremental() {
		// for testing
		return this.titlesBloomFilterIncremental;
	}
	
	public List<String> getTitles(boolean getUniques) {
		return getTitles(this.dataset.size(), getUniques);
	}
	
	public List<String> getTitles() {
		return getTitles(this.dataset.size(), true);
	}
	
	public List<String> getTitles(int size) {
		return getTitles(size, true);
	}
	
	public void showPublicationsFast() {
		showPublicationsFast(this.dataset.size());
	}
	
	public void showPublicationsFast(int value) {
		for(int i=0;i<value;i++) {
			System.out.println(dataset.get(i).toString() + "\n");
		}
	}
	
	public String showPublications() {
		return showPublications(this.dataset.size());
	}
	
	public String showPublications(int value) {
		String content = "";
		for(int i=0;i<value;i++) {
			content += dataset.get(i).toString() + "\n";
		}
		return content;
	}
	
	public ArrayList<Publication> getDataset() {
		return this.dataset;
	}
	
	public String toString() {
		String maxvalues = String.valueOf(this.maxValues);
		if(this.maxValues == 0) {
			maxvalues = "no limit"; //"∞";
		}
		return "This dataset has " + this.dataset.size() + " values. Errors occurred: " + this.errors + ". Excluded by lack of trust: " + this.excluded + ". Max values: " + maxvalues + ". " + this.titlesBloomFilter.toString();
	}
	
	private String getContent(String[] parts, int place, String sep) {
		String content = "";
		for(int i=place;i<parts.length;i++) {
			content += parts[i];
			if(i != parts.length-1) {
				content += ",";
			}
		}
		return content;
	}
	
	private List<String> getLines(File fileName){
		List<String> lines = null;
		try {
			lines = Files.readAllLines(Paths.get(fileName.getAbsolutePath()));
		} catch (IOException e) {
			System.out.println("Error reading the lines of the file \"" + fileName.getAbsolutePath() + "\"");
			e.printStackTrace();
		}
		return lines;
	}
	
	/// ########################################################33
	public int getMaxValues() {
		return this.maxValues;
	}
	
	public int size() {
		return dataset.size();
	}
	
	public void showSimilarNews(double threshHold) {
		showSimilarNews(threshHold, 100, 10, true);
	}
	
	/// ########################################################33
	
}
