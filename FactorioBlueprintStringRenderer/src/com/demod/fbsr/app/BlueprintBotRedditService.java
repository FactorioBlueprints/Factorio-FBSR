package com.demod.fbsr.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.demod.factorio.Config;
import com.demod.factorio.Utils;
import com.demod.fbsr.Blueprint;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.BlueprintStringData;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.TaskReporting;
import com.demod.fbsr.WebUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Uninterruptibles;

import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.InboxManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Message;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;
import net.dean.jraw.paginators.CommentStream;
import net.dean.jraw.paginators.InboxPaginator;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.SubredditPaginator;
import net.dean.jraw.paginators.TimePeriod;

public class BlueprintBotRedditService extends AbstractScheduledService {

	private static final String WATCHDOG_LABEL = "Reddit Bot";

	private static final File CACHE_FILE = new File("redditCache.json");
	private static final String REDDIT_AUTHOR_URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/43/Reddit.svg/64px-Reddit.svg.png";

	private ObjectNode configJson;
	private String myUserName;
	private List<String> subreddits;
	private long ageLimitMillis;
	private Credentials credentials;

	private RedditClient reddit;
	private AccountManager account;
	private OAuthData authData;

	private long authExpireMillis = 0;
	private boolean processMessages;
	private String summonKeyword;
	private String myUserNameMention;

	private void ensureConnectedToReddit() throws NetworkException, OAuthException, InterruptedException {
		if (System.currentTimeMillis() + 60000 > authExpireMillis) {
			for (int wait = 4000; true; wait = Math.min(wait * 2, (5) * 60 * 1000)) {
				try {
					System.out.println("Connecting to Reddit...");
					authData = reddit.getOAuthHelper().easyAuth(credentials);
					authExpireMillis = authData.getExpirationDate().getTime();
					reddit.authenticate(authData);
					System.out.println("Reconnected to Reddit!");
					break;
				} catch (Exception e) {
					System.out.println("[Waiting " + TimeUnit.MILLISECONDS.toSeconds(wait)
							+ " seconds] Connection Failure [" + e.getClass().getSimpleName() + "]: " + e.getMessage());
					Thread.sleep(wait);
				}
			}
		}
	}

	private Optional<Comment> getMyReply(CommentNode comments) {
		return comments.getChildren().stream().map(c -> c.getComment()).filter(c -> c.getAuthor().equals(myUserName))
				.findAny();
	}

	private ObjectNode getOrCreateCache() {
		if (CACHE_FILE.exists()) {
			try (FileInputStream fis = new FileInputStream(CACHE_FILE)) {
				return Utils.readJsonFromStream(fis);
			} catch (Exception e) {
				// No worries if anything went wrong with the file.
			}
		}

		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode cache = objectMapper.createObjectNode();
		cache.put("lastProcessedMessageMillis", 0L);
		return cache;
	}

	private String getPermaLink(Comment comment) {
		try {
			return "http://www.reddit.com/r/" + comment.getSubredditName() + "/comments/"
					+ comment.getSubmissionId().split("_")[1] + "/_/" + comment.getId();
		} catch (Exception e) {
			return "!!! Failed to create permalink! " + comment.getSubmissionId() + " !!!";
		}
	}

	private String getPermaLink(Message message) {
		return "https://www.reddit.com/message/messages/" + message.getId();
	}

	private Optional<String> processContent(String content, String link, String category, String author,
			Optional<WatchdogService> watchdog) {
		String contentLowerCase = content.toLowerCase();
		if (!contentLowerCase.contains(summonKeyword) && !contentLowerCase.contains(myUserNameMention)) {
			return Optional.empty();
		}

		TaskReporting reporting = new TaskReporting();
		reporting.setContext(content);
		reporting.addLink(link);

		try {
			List<BlueprintStringData> blueprintStrings = BlueprintFinder.search(content, reporting);
			List<Blueprint> blueprints = blueprintStrings.stream().flatMap(s -> s.getBlueprints().stream())
					.collect(Collectors.toList());

			for (Blueprint blueprint : blueprints) {
				watchdog.ifPresent(w -> w.notifyActive(WATCHDOG_LABEL));
				try {
					BufferedImage image = FBSR.renderBlueprint(blueprint, reporting);
					reporting.addImage(blueprint.getLabel(),
							WebUtils.uploadToHostingService("blueprint.png", image).toString());
				} catch (Exception e) {
					reporting.addException(e);
				}
			}
		} catch (Exception e) {
			reporting.addException(e);
		}

		List<String> lines = new ArrayList<>();
		List<Entry<Optional<String>, String>> images = reporting.getImages();
		if (images.size() > 1) {
			int id = 1;
			List<Entry<URL, String>> links = new ArrayList<>();
			for (Entry<Optional<String>, String> pair : images) {
				Optional<String> label = pair.getKey();
				String url = pair.getValue();
				try {
					links.add(new SimpleEntry<>(new URL(url), label.orElse(null)));
				} catch (MalformedURLException e) {
					reporting.addException(e);
				}
			}

			// FIXME
			// Optional<URL> albumUrl;
			// try {
			// albumUrl = Optional
			// .of(WebUtils.uploadToBundly("Blueprint Images", "Renderings provided by
			// Blueprint Bot", links));
			// } catch (IOException e) {
			// reporting.addException(e);
			// albumUrl = Optional.empty();
			// }
			// if (albumUrl.isPresent()) {
			// lines.add("Blueprint Images ([View As Album](" + albumUrl.get() + ")):\n");
			// } else {
			lines.add("Blueprint Images:");
			// }

			for (Entry<Optional<String>, String> pair : images) {
				Optional<String> label = pair.getKey();
				String url = pair.getValue();
				lines.add("[" + (id++) + ": " + label.orElse("Blueprint") + "](" + url + ")");
			}
		} else if (!images.isEmpty()) {
			Entry<Optional<String>, String> pair = images.get(0);
			Optional<String> label = pair.getKey();
			String url = pair.getValue();
			lines.add("[Blueprint Image" + label.map(s -> " (" + s + ")").orElse("") + "](" + url + ")");
		}

		for (String info : reporting.getInfo()) {
			lines.add("    " + info);
		}

		if (!reporting.getExceptions().isEmpty()) {
			lines.add(
					"    There was a problem completing your request. I have contacted my programmer to fix it for you!");
		}

		ServiceFinder.findService(BlueprintBotDiscordService.class)
				.ifPresent(s -> s.sendReport("Reddit / " + category + " / " + author, REDDIT_AUTHOR_URL, reporting));

		if (!lines.isEmpty()) {
			return Optional.of(lines.stream().collect(Collectors.joining("\n\n")));
		} else {
			return Optional.empty();
		}
	}

	private boolean processNewComments(
			ObjectNode cacheJson, String subreddit, long ageLimitMillis,
			Optional<WatchdogService> watchdog) throws ApiException, IOException {
		long lastProcessedMillis = cacheJson.path("lastProcessedCommentMillis-" + subreddit).asLong(0L);

		CommentStream commentStream = new CommentStream(reddit, subreddit);
		commentStream.setTimePeriod(TimePeriod.ALL);
		commentStream.setSorting(Sorting.NEW);

		int processedCount = 0;
		long newestMillis = lastProcessedMillis;
		List<Entry<Comment, String>> pendingReplies = new LinkedList<>();
		paginate: for (Listing<Comment> listing : commentStream) {
			for (Comment comment : listing) {
				long createMillis = comment.getCreated().getTime();
				if (createMillis <= lastProcessedMillis
						|| (System.currentTimeMillis() - createMillis > ageLimitMillis)) {
					break paginate;
				}
				processedCount++;
				newestMillis = Math.max(newestMillis, createMillis);

				if (comment.getAuthor().equals(myUserName)) {
					break paginate;
				}

				if (comment.isArchived()) {
					continue;
				}

				Optional<String> response = processContent(comment.getBody(), getPermaLink(comment),
						comment.getSubredditName(), comment.getAuthor(), watchdog);
				if (response.isPresent()) {
					pendingReplies.add(new SimpleEntry<>(comment, response.get()));
				}
			}
		}
		for (Entry<Comment, String> pair : pendingReplies) {
			System.out.println("IM TRYING TO REPLY TO A COMMENT!");
			String message = pair.getValue();
			if (message.length() > 10000) {
				message = WebUtils.uploadToHostingService("MESSAGE_TOO_LONG.txt", message.getBytes()).toString();
			}

			while (true) {
				try {
					account.reply(pair.getKey(), message);
					break;
				} catch (ApiException e) {
					if (e.getReason().equals("RATELIMIT")) {
						System.out.println("RATE LIMITED! WAITING 6 MINUTES...");
						Uninterruptibles.sleepUninterruptibly(6, TimeUnit.MINUTES);
					} else {
						throw e;
					}
				}
			}
		}

		if (processedCount > 0) {
			System.out.println("Processed " + processedCount + " comment(s) from /r/" + subreddit);
			cacheJson.put("lastProcessedCommentMillis-" + subreddit, newestMillis);
			return true;
		} else {
			return false;
		}
	}

	private boolean processNewMessages(ObjectNode cacheJson, long ageLimitMillis, Optional<WatchdogService> watchdog)
			throws ApiException {

		JsonNode lastProcessedMessageMillis = cacheJson.path("lastProcessedMessageMillis");
		assert lastProcessedMessageMillis.isIntegralNumber();
		long lastProcessedMillis = lastProcessedMessageMillis.longValue();

		InboxPaginator paginator = new InboxPaginator(reddit, "messages");
		paginator.setTimePeriod(TimePeriod.ALL);
		paginator.setSorting(Sorting.NEW);

		int processedCount = 0;
		long newestMillis = lastProcessedMillis;
		List<Entry<Message, String>> pendingReplies = new LinkedList<>();
		List<Message> processedMessages = new LinkedList<>();
		paginate: for (Listing<Message> listing : paginator) {
			for (Message message : listing) {
				if (message.isRead()) {
					break paginate;
				}

				long createMillis = message.getCreated().getTime();
				if (createMillis <= lastProcessedMillis
						|| (System.currentTimeMillis() - createMillis > ageLimitMillis)) {
					break paginate;
				}

				processedCount++;
				newestMillis = Math.max(newestMillis, createMillis);
				processedMessages.add(message);

				Optional<String> response = processContent(message.getBody(), getPermaLink(message), "(Private)",
						message.getAuthor(), watchdog);
				if (response.isPresent()) {
					pendingReplies.add(new SimpleEntry<>(message, response.get()));
				}
			}
		}

		if (!processedMessages.isEmpty()) {
			new InboxManager(reddit).setRead(true, processedMessages.get(0),
					processedMessages.stream().skip(1).toArray(Message[]::new));
		}

		for (Entry<Message, String> pair : pendingReplies) {
			System.out.println("IM TRYING TO REPLY TO A MESSAGE!");
			while (true) {
				try {
					account.reply(pair.getKey(), pair.getValue());
					break;
				} catch (ApiException e) {
					if (e.getReason().equals("RATELIMIT")) {
						System.out.println("RATE LIMITED! WAITING 6 MINUTES...");
						Uninterruptibles.sleepUninterruptibly(6, TimeUnit.MINUTES);
					} else {
						throw e;
					}
				}
			}
		}

		if (processedCount > 0) {
			System.out.println("Processed " + processedCount + " message(s)");
			cacheJson.put("lastProcessedMessageMillis", newestMillis);
			return true;
		} else {
			return false;
		}
	}

	private boolean processNewSubmissions(
			ObjectNode cacheJson, String subreddit, long ageLimitMillis,
			Optional<WatchdogService> watchdog) throws NetworkException, ApiException {
		JsonNode lastProcessedSubmissionMillis = cacheJson.path("lastProcessedSubmissionMillis-" + subreddit);
		assert lastProcessedSubmissionMillis.isIntegralNumber();
		long lastProcessedMillis = lastProcessedSubmissionMillis.longValue();

		SubredditPaginator paginator = new SubredditPaginator(reddit, subreddit);
		paginator.setTimePeriod(TimePeriod.ALL);
		paginator.setSorting(Sorting.NEW);

		int processedCount = 0;
		long newestMillis = lastProcessedMillis;
		List<Entry<Submission, String>> pendingReplies = new LinkedList<>();
		paginate: for (Listing<Submission> listing : paginator) {
			for (Submission submission : listing) {
				long createMillis = submission.getCreated().getTime();
				if (createMillis <= lastProcessedMillis
						|| (System.currentTimeMillis() - createMillis > ageLimitMillis)) {
					break paginate;
				}
				processedCount++;
				newestMillis = Math.max(newestMillis, createMillis);

				if (!submission.isSelfPost() || submission.isLocked() || submission.isArchived()) {
					continue;
				}

				CommentNode comments = submission.getComments();
				if (comments == null && submission.getCommentCount() > 0) {
					submission = reddit.getSubmission(submission.getId());
					comments = submission.getComments();
				}
				if (comments != null && getMyReply(comments).isPresent()) {
					break paginate;
				}

				Optional<String> response = processContent(submission.getSelftext(), submission.getUrl(),
						submission.getSubredditName(), submission.getAuthor(), watchdog);
				if (response.isPresent()) {
					pendingReplies.add(new SimpleEntry<>(submission, response.get()));
				}
			}
		}
		for (Entry<Submission, String> pair : pendingReplies) {
			System.out.println("IM TRYING TO REPLY TO A SUBMISSION!");
			while (true) {
				try {
					account.reply(pair.getKey(), pair.getValue());
					break;
				} catch (ApiException e) {
					if (e.getReason().equals("RATELIMIT")) {
						System.out.println("RATE LIMITED! WAITING 6 MINUTES...");
						Uninterruptibles.sleepUninterruptibly(6, TimeUnit.MINUTES);
					} else {
						throw e;
					}
				}
			}
		}

		if (processedCount > 0) {
			System.out.println("Processed " + processedCount + " submission(s) from /r/" + subreddit);
			cacheJson.put("lastProcessedSubmissionMillis-" + subreddit, newestMillis);
			return true;
		} else {
			return false;
		}
	}

	public void processRequest(String... ids) throws NetworkException, ApiException {
		System.out.println("REQUESTED: " + Arrays.toString(ids));
		Listing<Thing> listing = reddit.get(ids);
		System.out.println("REQUESTED RESULT = " + listing.size());
		for (Thing thing : listing) {
			if (thing instanceof Comment) {
				System.out.println("REQUESTED COMMENT!");
				Comment comment = (Comment) thing;
				Optional<String> response = processContent(comment.getBody(), getPermaLink(comment),
						comment.getSubredditName(), comment.getAuthor(), Optional.empty());
				if (response.isPresent()) {
					account.reply(comment, response.get());
				}
			} else if (thing instanceof Submission) {
				System.out.println("REQUESTED SUBMISSION!");
				Submission submission = (Submission) thing;
				Optional<String> response = processContent(submission.getSelftext(), submission.getUrl(),
						submission.getSubredditName(), submission.getAuthor(), Optional.empty());
				if (response.isPresent()) {
					account.reply(submission, response.get());
				}
			}
		}
	}

	@Override
	protected void runOneIteration() throws Exception {
		Optional<WatchdogService> watchdog = ServiceFinder.findService(WatchdogService.class);
		watchdog.ifPresent(w -> w.notifyKnown(WATCHDOG_LABEL));

		try {
			ObjectNode cacheJson = getOrCreateCache();
			boolean cacheUpdated = false;

			ensureConnectedToReddit();

			for (String subreddit : subreddits) {
				cacheUpdated |= processNewSubmissions(cacheJson, subreddit, ageLimitMillis, watchdog);
				cacheUpdated |= processNewComments(cacheJson, subreddit, ageLimitMillis, watchdog);
			}

			if (processMessages) {
				cacheUpdated |= processNewMessages(cacheJson, ageLimitMillis, watchdog);
			}

			if (cacheUpdated) {
				saveCache(cacheJson);
			}

			watchdog.ifPresent(w -> w.notifyActive(WATCHDOG_LABEL));
		} catch (NetworkException e) {
			System.out.println("Network Problem [" + e.getClass().getSimpleName() + "]: " + e.getMessage());
			authExpireMillis = 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void saveCache(JsonNode cacheJson) throws IOException {
		ObjectWriter objectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
		try (FileWriter fw = new FileWriter(CACHE_FILE)) {
			fw.write(objectWriter.writeValueAsString(cacheJson));
		}
	}

	@Override
	protected Scheduler scheduler() {
		JsonNode refreshSeconds = configJson.path("refresh_seconds");
		assert refreshSeconds.isIntegralNumber();
		return Scheduler.newFixedDelaySchedule(0, refreshSeconds.intValue(), TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown() throws Exception {
		ServiceFinder.removeService(this);
		reddit.getOAuthHelper().revokeAccessToken(credentials);
		reddit.deauthenticate();
	}

	@Override
	protected void startUp() {
		reddit = new RedditClient(UserAgent.of("server", "com.demod.fbsr", "0.0.1", "demodude4u"));
		account = new AccountManager(reddit);

		configJson = (ObjectNode) Config.get().path("reddit");
		if (configJson.has("subreddit")) {
			JsonNode subreddit = configJson.path("subreddit");
			assert subreddit.isTextual();
			subreddits = ImmutableList.of(subreddit.textValue());
		} else {
			ArrayNode subredditsJson = (ArrayNode) configJson.path("subreddits");
			subreddits = new ArrayList<>();
			Utils.<String>forEach(subredditsJson, s -> subreddits.add(s));
		}
		JsonNode ageLimitHours = configJson.path("age_limit_hours");
		assert ageLimitHours.isIntegralNumber();
		ageLimitMillis = ageLimitHours.intValue() * 60 * 60 * 1000;
		JsonNode processMessages = configJson.path("process_messages");
		assert processMessages.isBoolean();
		this.processMessages = processMessages.booleanValue();
		JsonNode summonKeyword = configJson.path("summon_keyword");
		assert summonKeyword.isTextual();
		this.summonKeyword = summonKeyword.textValue().toLowerCase();

		ObjectNode redditCredentialsJson = (ObjectNode) configJson.path("credentials");
		JsonNode username = redditCredentialsJson.path("username");
		JsonNode password = redditCredentialsJson.path("password");
		JsonNode clientId = redditCredentialsJson.path("client_id");
		JsonNode clientSecret = redditCredentialsJson.path("client_secret");
		assert username.isTextual();
		assert password.isTextual();
		assert clientId.isTextual();
		assert clientSecret.isTextual();
		credentials = Credentials.script( //
				username.textValue(), //
				password.textValue(), //
				clientId.textValue(), //
				clientSecret.textValue() //
		);

		myUserName = username.textValue();
		myUserNameMention = ("u/" + myUserName).toLowerCase();

		ServiceFinder.addService(this);
	}
}
