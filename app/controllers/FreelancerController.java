package controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import models.APIResponse;
import models.ProcessProjects;
import models.SearchResult;

import play.cache.NamedCache;
import play.cache.SyncCacheApi;

import play.libs.ws.WSResponse;

import play.mvc.Controller;
import play.mvc.Result;

import play.libs.ws.WSClient;

import models.FreelancerProject;
import views.html.*;

import javax.inject.Inject;

/**
 * Freelancer controller class
 * 
 * @author Darshak Kachchhi, Mansi Lakhani and Apeksha Gohil
 *
 */
public class FreelancerController extends Controller {

	private WSClient ws;

	@NamedCache("session-cache")
	private SyncCacheApi cache;

	private String baseURL = "https://www.freelancer.com/api";
	private List<FreelancerProject> projects = new ArrayList<FreelancerProject>();
	private List<SearchResult> searchResults = new ArrayList<SearchResult>();
	private static final int RESULT_COUNT = 10;

	@Inject
	public FreelancerController(WSClient ws, SyncCacheApi cache) {
		this.ws = ws;
		this.cache = cache;
	}

	/**
	 * An action that renders an HTML page with a Global stats page of latest 250
	 * projects from all the search has beed made till now. The configuration in the
	 * <code>routes</code> file means that this method will be called when the
	 * application receives a <code>GET</code> request with a path of
	 * <code>/globalstats</code>.
	 * 
	 * @author Darshak Kachchhi
	 * 
	 * @return Result of global states to render the result on HTML page.
	 */
	public Result globalStats() {
		LinkedHashMap<String, Long> map = ProcessProjects.getGlobalStats(searchResults);
		return ok(stats.render("global", map));
	}

	/**
	 * An action that renders an HTML page with a local stats page of selected
	 * project. The configuration in the <code>routes</code> file means that this
	 * method will be called when the application receives a <code>GET</code>
	 * request with a path of <code>/localstats</code>.
	 * 
	 * @author Darshak Kachchhi
	 * 
	 * @param projectID Local stats of projectID to print
	 * @return Result of global states to render the result on HTML page.
	 */
	public Result localStats(String projectID) {
		LinkedHashMap<String, Long> map = ProcessProjects.getLocalStatByProjectId(searchResults, projectID);
		return ok(stats.render("local", map));
	}

	public Result skills(String skillName) {
		return ok(search.render("ownerID"));
	}

	/**
	 * Freelancer API call to fetch data from the API and render into the html page.
	 * API data will be fetch as a JSON Data and then using the ObjectMapper,
	 * convert the data into data model of application.
	 * 
	 * @author Darshak Kachchhi, Mansi Lakhani
	 * @param inputKeyword fetch result for given keyword
	 */
	private CompletionStage<Result> fetchData(String inputKeyword) {

		Optional<List<SearchResult>> cachedSearchResults = cache.get("cachedSearchResults");
		if (cachedSearchResults.isPresent()) {
			searchResults = cachedSearchResults.get();
			Optional<SearchResult> previousSearchOption = ProcessProjects.getProjectByQuery(searchResults, inputKeyword);
			if(previousSearchOption.isPresent()){
				SearchResult previousSearch = previousSearchOption.get();
				searchResults = ProcessProjects.removeProjectResult(searchResults, inputKeyword);
				searchResults.add(0, previousSearch);
				return CompletableFuture.completedFuture(ok(index.render(searchResults)));
			}

		}



		return ws.url(baseURL + "/projects/0.1/projects/active")
				.addHeader("freelancer-oauth-v1", "l12Bz0qvwEkZVSvwzFds2EBSGGhDqa")
				.addQueryParameter("job_details", "true").addQueryParameter("query", inputKeyword)
				.addQueryParameter("limit", String.valueOf(FreelancerController.RESULT_COUNT))
				.addQueryParameter("compact", "true").get().thenApplyAsync(WSResponse::asJson).toCompletableFuture()
				.thenApplyAsync(result -> {
					ObjectMapper mapper = new ObjectMapper();
					mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

					try {
						APIResponse project = mapper.treeToValue(result, APIResponse.class);
						SearchResult searchResult = new SearchResult();
						searchResult.setQuery(inputKeyword);
						searchResult.setProjects(project.getResult().getProjects());

						searchResults.add(0, searchResult);

						cache.set("cachedSearchResults", searchResults, 15*60);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}

					return ok(index.render(searchResults));
				});
	}

	/**
	 * An action that renders an HTML page with 10 latest projects of given keyword
	 * and it will be rendered on the top of the previous result of projects. The
	 * configuration in the <code>routes</code> file means that this method will be
	 * called when the application receives a <code>GET</code> request with a path
	 * of <code>/search/:inputKeyword</code>.
	 * 
	 * @author Darshak Kachchhi, Mansi Lakhani
	 * @param inputKeyword fetch result for given keyword which is entered by the
	 *                     user into text box
	 * @return
	 */
	public CompletionStage<Result> search(String inputKeyword) {
		return fetchData(inputKeyword);
	}

	/**
	 * An action that renders an HTML page with a welcome message. The configuration
	 * in the <code>routes</code> file means that this method will be called when
	 * the application receives a <code>GET</code> request with a path of
	 * <code>/</code>.
	 */
	public CompletionStage<Result> index() {
		searchResults.clear();
		return CompletableFuture.completedFuture(ok(index.render(searchResults)));
	}

	public Result profile(String ownerID) {
		return ok(search.render(ownerID));
	}


//	private Optional<RequestToken> getSessionTokenPair() {
//		if (Http..containsKey("token")) {
//			return Optional.of(new RequestToken(session("token"), session("secret")));
//		}
//		return Optional.empty();
//	}
}
