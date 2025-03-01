package io.github.redouane59.twitter;

import static io.github.redouane59.twitter.dto.endpoints.AdditionalParameters.MAX_RESULTS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.scribejava.apis.TwitterApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.HttpClientConfig;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import io.github.redouane59.RelationType;
import io.github.redouane59.twitter.dto.collections.CollectionsResponse;
import io.github.redouane59.twitter.dto.collections.TimeLineOrder;
import io.github.redouane59.twitter.dto.dm.DirectMessage;
import io.github.redouane59.twitter.dto.dm.DmParameters;
import io.github.redouane59.twitter.dto.dm.DmParameters.DmMessage;
import io.github.redouane59.twitter.dto.dm.PostDmResponse;
import io.github.redouane59.twitter.dto.dm.deprecatedV1.DmListAnswer;
import io.github.redouane59.twitter.dto.endpoints.AdditionalParameters;
import io.github.redouane59.twitter.dto.getrelationship.IdList;
import io.github.redouane59.twitter.dto.getrelationship.RelationshipObjectResponse;
import io.github.redouane59.twitter.dto.list.TwitterList;
import io.github.redouane59.twitter.dto.list.TwitterList.TwitterListData;
import io.github.redouane59.twitter.dto.list.TwitterListList;
import io.github.redouane59.twitter.dto.list.TwitterListMember.TwitterListMemberData;
import io.github.redouane59.twitter.dto.others.BearerToken;
import io.github.redouane59.twitter.dto.others.BlockResponse;
import io.github.redouane59.twitter.dto.others.RateLimitStatus;
import io.github.redouane59.twitter.dto.others.RequestToken;
import io.github.redouane59.twitter.dto.rules.FilteredStreamRulePredicate;
import io.github.redouane59.twitter.dto.space.Space;
import io.github.redouane59.twitter.dto.space.SpaceList;
import io.github.redouane59.twitter.dto.space.SpaceState;
import io.github.redouane59.twitter.dto.stream.StreamRules;
import io.github.redouane59.twitter.dto.stream.StreamRules.StreamMeta;
import io.github.redouane59.twitter.dto.stream.StreamRules.StreamRule;
import io.github.redouane59.twitter.dto.tweet.HiddenResponse;
import io.github.redouane59.twitter.dto.tweet.HiddenResponse.HiddenData;
import io.github.redouane59.twitter.dto.tweet.LikeResponse;
import io.github.redouane59.twitter.dto.tweet.MediaCategory;
import io.github.redouane59.twitter.dto.tweet.RetweetResponse;
import io.github.redouane59.twitter.dto.tweet.Tweet;
import io.github.redouane59.twitter.dto.tweet.TweetCountsList;
import io.github.redouane59.twitter.dto.tweet.TweetList;
import io.github.redouane59.twitter.dto.tweet.TweetList.TweetMeta;
import io.github.redouane59.twitter.dto.tweet.TweetParameters;
import io.github.redouane59.twitter.dto.tweet.TweetSearchResponseV1;
import io.github.redouane59.twitter.dto.tweet.TweetV1;
import io.github.redouane59.twitter.dto.tweet.TweetV1Deserializer;
import io.github.redouane59.twitter.dto.tweet.TweetV2;
import io.github.redouane59.twitter.dto.tweet.UploadMediaResponse;
import io.github.redouane59.twitter.dto.user.FollowBody;
import io.github.redouane59.twitter.dto.user.User;
import io.github.redouane59.twitter.dto.user.UserActionResponse;
import io.github.redouane59.twitter.dto.user.UserList;
import io.github.redouane59.twitter.dto.user.UserList.UserMeta;
import io.github.redouane59.twitter.dto.user.UserV2;
import io.github.redouane59.twitter.dto.user.UserV2.UserData;
import io.github.redouane59.twitter.helpers.AbstractRequestHelper;
import io.github.redouane59.twitter.helpers.ConverterHelper;
import io.github.redouane59.twitter.helpers.JsonHelper;
import io.github.redouane59.twitter.helpers.RequestHelper;
import io.github.redouane59.twitter.helpers.RequestHelperV2;
import io.github.redouane59.twitter.helpers.URLHelper;
import io.github.redouane59.twitter.signature.TwitterCredentials;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class TwitterClient implements ITwitterClientV1, ITwitterClientV2, ITwitterClientArchive {

  public static final String TWEET_FIELDS     = "tweet.fields";
  public static final String
                             ALL_TWEET_FIELDS =
      "attachments,author_id,created_at,entities,geo,id,in_reply_to_user_id,lang,possibly_sensitive,public_metrics,referenced_tweets,source,text,withheld,context_annotations,conversation_id,reply_settings";
  public static final String EXPANSION        = "expansions";
  public static final String
                             ALL_EXPANSIONS   =
      "author_id,entities.mentions.username,in_reply_to_user_id,referenced_tweets.id,referenced_tweets.id.author_id,attachments.media_keys,geo.place_id";
  public static final String USER_FIELDS      = "user.fields";
  public static final String ALL_USER_FIELDS  =
      "id,created_at,entities,username,name,location,url,verified,profile_image_url,public_metrics,pinned_tweet_id,description,protected";
  public static final String MEDIA_FIELD      = "media.fields";
  public static final String ALL_MEDIA_FIELDS =
      "duration_ms,height,media_key,preview_image_url,public_metrics,type,url,width,alt_text,variants";
  public static final String SPACE_FIELDS     = "space.fields";
  public static final String
                             ALL_SPACE_FIELDS =
      "host_ids,created_at,creator_id,id,lang,invited_user_ids,participant_count,speaker_ids,started_at,state,title,updated_at,scheduled_start,is_ticketed";
  public static final String PLACE_FIELDS     = "place.fields";
  public static final String ALL_PLACE_FIELDS = "contained_within,country,country_code,full_name,geo,id,name,place_type";
  public static final String POLL_FIELDS      = "poll.fields";
  public static final String ALL_POLL_FIELDS  = "duration_minutes,end_datetime,id,options,voting_status";
  public static final String LIST_FIELDS      = "list.fields";
  public static final String
                             ALL_LIST_FIELDS  = "created_at,follower_count,member_count,private,description,owner_id";

  public static final  String             ALL_SPACE_EXPANSIONS                 = "invited_user_ids,speaker_ids,creator_id,host_ids";
  public static final  String             DM_FIELDS                            = "dm_event.fields";
  public static final  String
                                          ALL_DM_FIELDS                        =
      "id,text,event_type,created_at,dm_conversation_id,sender_id,participant_ids,referenced_tweets,attachments";
  private static final String
                                          ALL_DM_EXPANSIONS                    =
      "attachments.media_keys,referenced_tweets.id,sender_id,participant_ids";
  private static final String             QUERY                                = "query";
  private static final String             CURSOR                               = "cursor";
  private static final String             NEXT                                 = "next";
  private static final String             PAGINATION_TOKEN                     = "pagination_token";
  private static final String             PINNED_TWEET_ID                      = "pinned_tweet_id";
  private static final String             BACKFILL_MINUTES                     = "backfill_minutes";
  private static final String             DATA                                 = "data";
  private static final String             DELETED                              = "deleted";
  private static final String             IS_MEMBER                            = "is_member";
  private static final String             FOLLOWING                            = "following";
  private static final String             PINNED                               = "pinned";
  private static final String[]           DEFAULT_VALID_CREDENTIALS_FILE_NAMES = {"test-twitter-credentials.json",
                                                                                  "twitter-credentials.json"};
  private              URLHelper          urlHelper                            = new URLHelper();
  private              RequestHelper      requestHelperV1;
  private              RequestHelperV2    requestHelperV2;
  private              TwitterCredentials twitterCredentials;

  public TwitterClient() {
    this(getAuthentication());
  }

  public TwitterClient(TwitterCredentials credentials) {
    this(credentials, new ServiceBuilder(credentials.getApiKey()).apiSecret(credentials.getApiSecretKey()));
  }

  public TwitterClient(TwitterCredentials credentials, HttpClient httpClient) {
    this(credentials,
         new ServiceBuilder(credentials.getApiKey()).apiSecret(credentials.getApiSecretKey()).httpClient(httpClient));
  }

  public TwitterClient(TwitterCredentials credentials, HttpClient httpClient, HttpClientConfig config) {
    this(credentials, new ServiceBuilder(credentials.getApiKey()).apiSecret(credentials.getApiSecretKey())
                                                                 .httpClient(httpClient).httpClientConfig(config));
  }

  public TwitterClient(TwitterCredentials credentials, ServiceBuilder serviceBuilder) {
    this(credentials, serviceBuilder.apiKey(credentials.getApiKey()).apiSecret(credentials.getApiSecretKey())
                                    .build(TwitterApi.instance()));
  }

  public TwitterClient(TwitterCredentials credentials, OAuth10aService service) {
    twitterCredentials = credentials;
    requestHelperV1    = new RequestHelper(credentials, service);
    requestHelperV2    = new RequestHelperV2(credentials, service);
  }

  public static TwitterCredentials getAuthentication() {
    String credentialPath = System.getProperty("twitter.credentials.file.path");
    if (credentialPath != null) {
      return getAuthentication(new File(credentialPath));
    } else {
      return getAuthentication(Paths.get(""));
    }
  }

  public static TwitterCredentials getAuthentication(final Path pathToScan, final String... validNames) {
    if (pathToScan.toFile().isFile()) {
      return getAuthentication(pathToScan.toFile());
    } else {
      String[] namesToCheck = validNames != null && validNames.length > 0 ? validNames : DEFAULT_VALID_CREDENTIALS_FILE_NAMES;
      for (Path currentPath = pathToScan; currentPath != null; currentPath = currentPath.getParent()) {
        for (String name : namesToCheck) {
          Path file = currentPath.resolve(name);
          if (Files.isRegularFile(file)) {
            return getAuthentication(file.toFile());
          }
        }
      }
    }
    return null;
  }

  public static TwitterCredentials getAuthentication(File twitterCredentialsFile) {
    try {
      TwitterCredentials twitterCredentials = JsonHelper.OBJECT_MAPPER.readValue(twitterCredentialsFile, TwitterCredentials.class);
      if (twitterCredentials.getAccessToken() == null) {
        LOGGER.error("Access token is null in twitter-credentials.json");
      }
      if (twitterCredentials.getAccessTokenSecret() == null) {
        LOGGER.error("Secret token is null in twitter-credentials.json");
      }
      if (twitterCredentials.getApiKey() == null) {
        LOGGER.error("Consumer key is null in twitter-credentials.json");
      }
      if (twitterCredentials.getApiSecretKey() == null) {
        LOGGER.error("Consumer secret is null in twitter-credentials.json");
      }
      return twitterCredentials;
    } catch (Exception e) {
      LOGGER.error("Twitter credentials json file error in path {}. Use program argument -Dtwitter.credentials.file.path=/my/path/to/json.",
                   twitterCredentialsFile.getAbsolutePath(), e);
      return null;
    }
  }

  /**
   * Define the default behavior when Twitter API limits are reached (default value is true)
   *
   * @param automaticRetry false will raise a LimitExceededException, true will wait and call the endpoint again once the limit is over
   */
  public void setAutomaticRetry(boolean automaticRetry) {
    requestHelperV1.setAutomaticRetry(automaticRetry);
    requestHelperV2.setAutomaticRetry(automaticRetry);
  }

  // can manage up to 5000 results / call . Max 15 calls / 15min ==> 75.000
  // results max. / 15min
  private List<String> getUserIdsByRelation(String url) {
    String       cursor = "-1";
    List<String> result = new ArrayList<>();
    do {
      String           urlWithCursor  = url + "&" + CURSOR + "=" + cursor;
      Optional<IdList> idListResponse = getRequestHelper().getRequest(urlWithCursor, IdList.class);
      if (!idListResponse.isPresent()) {
        break;
      }
      result.addAll(idListResponse.get().getIds());
      cursor = idListResponse.get().getNextCursor();
    } while (!cursor.equals("0"));
    return result;
  }

  @Override
  public UserList getFollowers(String userId) {
    return getFollowers(userId, AdditionalParameters.builder().maxResults(1000).build());
  }

  @Override
  public UserList getFollowers(final String userId, final AdditionalParameters additionalParameters) {
    String              url        = urlHelper.getFollowersUrl(userId);
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelper().getRequestWithParameters(url, parameters, UserList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(1000));
    }
    return getUsersRecursively(url, parameters, getRequestHelper());
  }

  @Override
  public UserList getFollowing(String userId) {
    return getFollowing(userId, AdditionalParameters.builder().maxResults(1000).build());
  }

  @Override
  public UserList getFollowing(final String userId, final AdditionalParameters additionalParameters) {
    String              url        = urlHelper.getFollowingUrl(userId);
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelper().getRequestWithParameters(url, parameters, UserList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(1000));
    }
    return getUsersRecursively(url, parameters, getRequestHelper());
  }

  @Override
  public RelationType getRelationType(String userId1, String userId2) {
    String url = urlHelper.getFriendshipUrl(userId1, userId2);
    RelationshipObjectResponse relationshipDTO = getRequestHelper().getRequest(url, RelationshipObjectResponse.class)
                                                                   .orElseThrow(NoSuchElementException::new);
    boolean followedBy = relationshipDTO.getRelationship().getSource().isFollowedBy();
    boolean following  = relationshipDTO.getRelationship().getSource().isFollowing();
    if (followedBy && following) {
      return RelationType.FRIENDS;
    } else if (!followedBy && !following) {
      return RelationType.NONE;
    } else if (followedBy) {
      return RelationType.FOLLOWER;
    } else {
      return RelationType.FOLLOWING;
    }
  }

  @Override
  public List<String> getFollowersIds(String userId) {
    String url = urlHelper.getFollowersIdsUrl(userId);
    return getUserIdsByRelation(url);
  }

  @Override
  public List<String> getFollowingIds(String userId) {
    String url = urlHelper.getFollowingIdsUrl(userId);
    return getUserIdsByRelation(url);
  }

  @SneakyThrows
  @Override
  public UserActionResponse follow(String targetUserId) {
    String url  = urlHelper.getFollowUrl(getUserIdFromAccessToken());
    String body = JsonHelper.toJson(new FollowBody(targetUserId));
    return requestHelperV1.postRequestWithBodyJson(url, new HashMap<>(), body, UserActionResponse.class)
                          .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserActionResponse unfollow(String targetUserId) {
    String url = urlHelper.getUnfollowUrl(getUserIdFromAccessToken(), targetUserId);
    return getRequestHelper().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, UserActionResponse.class)
                             .orElseThrow(NoSuchElementException::new);

  }

  @SneakyThrows
  @Override
  public BlockResponse blockUser(final String targetUserId) {
    String url = urlHelper.getBlockUserUrl(getUserIdFromAccessToken());
    return getRequestHelper()
        .makeRequest(Verb.POST,
                     url,
                     new HashMap<>(),
                     JsonHelper.toJson(new FollowBody(targetUserId)),
                     true,
                     BlockResponse.class)
        .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public BlockResponse unblockUser(final String targetUserId) {
    String url = urlHelper.getUnblockUserUrl(getUserIdFromAccessToken(), targetUserId);
    return getRequestHelper().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, BlockResponse.class)
                             .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserList getBlockedUsers() {
    String              url        = urlHelper.getBlockingUsersUrl(getUserIdFromAccessToken());
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    return getRequestHelper().getRequestWithParameters(url, parameters, UserList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public User getUserFromUserId(String userId) {
    String              url        = getUrlHelper().getUserUrl(userId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    return getRequestHelper().getRequestWithParameters(url, parameters, UserV2.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserV2 getUserFromUserName(String userName) {
    String              url        = getUrlHelper().getUserUrlFromName(userName);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    return getRequestHelper().getRequestWithParameters(url, parameters, UserV2.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public List<User> getUsersFromUserNames(List<String> userNames) {
    String              url        = getUrlHelper().getUsersByUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    StringBuilder names = new StringBuilder();
    int           i     = 0;
    while (i < userNames.size() && i < URLHelper.MAX_LOOKUP) {
      String name = userNames.get(i);
      names.append(name);
      names.append(",");
      i++;
    }
    names.delete(names.length() - 1, names.length());
    parameters.put("usernames", names.toString());
    List<UserData> result = getRequestHelper().getRequestWithParameters(url, parameters, UserList.class)
                                              .orElseThrow(NoSuchElementException::new).getData();
    return result.stream().map(userData -> UserV2.builder().data(userData).build()).collect(Collectors.toList());
  }

  @Override
  public List<User> getUsersFromUserIds(List<String> userIds) {
    String              url        = getUrlHelper().getUsersUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    StringBuilder names = new StringBuilder();
    int           i     = 0;
    while (i < userIds.size() && i < URLHelper.MAX_LOOKUP) {
      String name = userIds.get(i);
      names.append(name);
      names.append(",");
      i++;
    }
    names.delete(names.length() - 1, names.length());
    parameters.put("ids", names.toString());
    List<UserData> result = getRequestHelper().getRequestWithParameters(url, parameters, UserList.class)
                                              .orElseThrow(NoSuchElementException::new).getData();
    return result.stream().map(userData -> UserV2.builder().data(userData).build()).collect(Collectors.toList());
  }

  @Override
  public RateLimitStatus getRateLimitStatus() {
    String url = URLHelper.RATE_LIMIT_URL;
    return getRequestHelper().getRequest(url, RateLimitStatus.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public LikeResponse likeTweet(String tweetId) {
    String url = getUrlHelper().getLikeUrl(getUserIdFromAccessToken());
    return getRequestHelperV1().postRequestWithBodyJson(url, new HashMap<>(), "{\"tweet_id\":\"" + tweetId + "\"}", LikeResponse.class)
                               .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public LikeResponse unlikeTweet(String tweetId) {
    String url = getUrlHelper().getUnlikeUrl(getUserIdFromAccessToken(), tweetId);
    return getRequestHelper()
        .makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, LikeResponse.class)
        .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserList getRetweetingUsers(String tweetId, int maxResults) {
    String              url        = urlHelper.getRetweetersUrl(tweetId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    return getUsersRecursively(maxResults, url, parameters);
  }

  // @todo see if it cannot be mixed with other similar function

  /**
   * Used for get liking users, get retweeting users and get members endpoints recursively calls
   */
  private UserList getUsersRecursively(int maxResults, String url, Map<String, String> parameters) {
    UserList result = UserList.builder().meta(new UserMeta()).build();
    String   next;

    do {
      parameters.put(MAX_RESULTS, String.valueOf(Math.min(100, maxResults - result.getData().size())));
      Optional<UserList> userList = getRequestHelper().getRequestWithParameters(url, parameters, UserList.class);
      if (!userList.isPresent() || userList.get().getData() == null) {
        result.getMeta().setNextToken(null);
        break;
      }
      result.getData().addAll(userList.get().getData());

      UserMeta meta = UserMeta.builder()
                              .resultCount(result.getData().size())
                              .nextToken(userList.get().getMeta().getNextToken())
                              .build();
      result.setMeta(meta);
      next = userList.get().getMeta().getNextToken();
      parameters.put(AdditionalParameters.PAGINATION_TOKEN, next);
    } while (next != null && result.getData().size() < maxResults);

    return result;
  }


  @Override
  public UserList getRetweetingUsers(String tweetId) {
    return getRetweetingUsers(tweetId, Integer.MAX_VALUE);
  }

  @Override
  public UserList getLikingUsers(final String tweetId, int maxResults) {
    String              url        = getUrlHelper().getLikingUsersUrl(tweetId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    return getUsersRecursively(maxResults, url, parameters);
  }

  @Override
  public UserList getLikingUsers(final String tweetId) {
    return getLikingUsers(tweetId, Integer.MAX_VALUE);
  }

  @Override
  public TweetList getLikedTweets(final String userId) {
    return getLikedTweets(userId, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public TweetList getLikedTweets(final String userId, AdditionalParameters additionalParameters) {
    String              url        = getUrlHelper().getLikedTweetsUrl(userId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelper().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }
    return getTweetsRecursively(url, parameters, getRequestHelper());
  }

  @Override
  public TweetCountsList getTweetCounts(final String query) {
    return getTweetCounts(query, AdditionalParameters.builder().build());
  }

  @Override
  public TweetCountsList getTweetCounts(final String query, AdditionalParameters additionalParameters) {
    String url = getUrlHelper().getTweetsCountUrl();
    return getTweetCounts(url, query, additionalParameters);
  }

  @Override
  public TweetCountsList getAllTweetCounts(final String query) {
    return getAllTweetCounts(query, AdditionalParameters.builder().build());
  }

  @Override
  public TweetCountsList getAllTweetCounts(final String query, AdditionalParameters additionalParameters) {
    String url = urlHelper.getTweetsCountAllUrl();
    return getTweetCounts(url, query, additionalParameters);
  }

  private TweetCountsList getTweetCounts(String url, final String query, AdditionalParameters additionalParameters) {
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(QUERY, query);
    return getRequestHelperV2().getRequestWithParameters(url, parameters, TweetCountsList.class).orElseThrow(NoSuchElementException::new);
  }

  @SneakyThrows
  @Override
  public UserActionResponse muteUser(final String userId) {
    String url  = urlHelper.getMuteUserUrl(getUserIdFromAccessToken());
    String body = JsonHelper.toJson(new FollowBody(userId));
    return requestHelperV1.postRequestWithBodyJson(url, new HashMap<>(), body, UserActionResponse.class)
                          .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserActionResponse unmuteUser(final String userId) {
    String url = urlHelper.getUnmuteUserUrl(getUserIdFromAccessToken(), userId);
    return requestHelperV1.makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, UserActionResponse.class)
                          .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserList getMutedUsers() {
    String              url        = urlHelper.getMutedUsersUrl(getUserIdFromAccessToken());
    Map<String, String> parameters = new HashMap<>();
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    parameters.put(MAX_RESULTS, "1000");
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    return requestHelperV1.getRequestWithParameters(url, parameters, UserList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public RetweetResponse retweetTweet(String tweetId) {
    String url  = getUrlHelper().getRetweetTweetUrl(getUserIdFromAccessToken());
    String body = "{\"tweet_id\": \"" + tweetId + "\"}";
    return requestHelperV1.postRequestWithBodyJson(url, new HashMap<>(), body, RetweetResponse.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public RetweetResponse unretweetTweet(final String tweetId) {
    String url = getUrlHelper().getUnretweetTweetUrl(getUserIdFromAccessToken(), tweetId);
    return requestHelperV1.makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, RetweetResponse.class)
                          .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public Space getSpace(final String spaceId) {
    String              url        = getUrlHelper().getSpaceUrl(spaceId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, "invited_user_ids,speaker_ids,creator_id,host_ids");
    parameters.put(SPACE_FIELDS, ALL_SPACE_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    return getRequestHelperV2().getRequestWithParameters(url, parameters, Space.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public SpaceList getSpaces(final List<String> spaceIds) {
    String              url        = getUrlHelper().getSpacesUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_SPACE_EXPANSIONS);
    parameters.put(SPACE_FIELDS, ALL_SPACE_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put("ids", String.join(", ", spaceIds));
    return getRequestHelperV2().getRequestWithParameters(url, parameters, SpaceList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public SpaceList getSpacesByCreators(final List<String> creatorIds) {
    Map<String, String> parameters = new HashMap<>();
    parameters.put("user_ids", String.join(", ", creatorIds));
    parameters.put(EXPANSION, ALL_SPACE_EXPANSIONS);
    parameters.put(SPACE_FIELDS, ALL_SPACE_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    return getRequestHelperV2().getRequestWithParameters(getUrlHelper().getSpaceByCreatorUrl(), parameters, SpaceList.class)
                               .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public SpaceList searchSpaces(final String query, final SpaceState state) {
    String              url        = getUrlHelper().getSearchSpacesUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put("query", query);
    parameters.put("state", state.getLabel());
    parameters.put(EXPANSION, ALL_SPACE_EXPANSIONS);
    parameters.put(SPACE_FIELDS, ALL_SPACE_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MAX_RESULTS, "100");
    return getRequestHelperV2().getRequestWithParameters(url, parameters, SpaceList.class)
                               .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserList getSpaceBuyers(final String spaceId) {
    String              url        = getUrlHelper().getSpaceBuyersUrl(spaceId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    return getRequestHelperV2().getRequestWithParameters(url, parameters, UserList.class)
                               .orElseThrow(NoSuchElementException::new);
  }

  @SneakyThrows
  @Override
  public TwitterList createList(final String listName, final String description, final boolean isPrivate) {
    String          url  = getUrlHelper().getListUrlV2();
    TwitterListData body = TwitterListData.builder().name(listName).description(description).isPrivate(isPrivate).build();
    return getRequestHelperV1().postRequestWithBodyJson(url, null, JsonHelper.toJson(body), TwitterList.class)
                               .orElseThrow(NoSuchElementException::new);
  }

  @Override
  public boolean deleteList(final String listId) {
    String url = getUrlHelper().getListUrlV2() + "/" + listId;
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(DELETED).asBoolean();

  }

  @SneakyThrows
  @Override
  public boolean addListMember(final String listId, final String userId) {
    String                url  = getUrlHelper().getAddListMemberUrl(listId);
    TwitterListMemberData body = TwitterListMemberData.builder().userId(userId).build();
    JsonNode jsonNode =
        getRequestHelperV1().postRequestWithBodyJson(url, null, JsonHelper.toJson(body), JsonNode.class)
                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(IS_MEMBER).asBoolean();
  }

  @Override
  public boolean removeListMember(final String listId, final String userId) {
    String url = getUrlHelper().getRemoveListMemberUrl(listId, userId);
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(IS_MEMBER).asBoolean();
  }

  @SneakyThrows
  @Override
  public boolean pinList(final String listId) {
    String url  = getUrlHelper().getPinListUrl(getUserIdFromAccessToken());
    String body = "{\"list_id\": \"" + listId + "\"}";
    JsonNode jsonNode = getRequestHelperV1().postRequestWithBodyJson(url, null, body, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(PINNED).asBoolean();
  }

  @Override
  public boolean unpinList(final String listId) {
    String url = getUrlHelper().getUnpinListUrl(getUserIdFromAccessToken(), listId);
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(PINNED).asBoolean();

  }

  @SneakyThrows
  @Override
  public boolean updateList(final String listId, final String listName, final String description, final boolean isPrivate) {
    String url = getUrlHelper().getListUrlV2() + "/" + listId;
    TwitterListData body = TwitterListData.builder()
                                          .name(listName).description(description).isPrivate(isPrivate).build();
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.PUT, url, new HashMap<>(), JsonHelper.toJson(body),
                                                         true, JsonNode.class).orElseThrow(NoSuchElementException::new);
    return jsonNode.get("updated").asBoolean();
  }

  @Override
  public boolean followList(final String listId) {
    String url  = getUrlHelper().getFollowListUrl(getUserIdFromAccessToken());
    String body = "{\"list_id\": \"" + listId + "\"}";
    JsonNode jsonNode = getRequestHelperV1().postRequestWithBodyJson(url, null, body, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(FOLLOWING).asBoolean();
  }

  @Override
  public boolean unfollowList(final String listId) {
    String url = getUrlHelper().getUnfollowListUrl(getUserIdFromAccessToken(), listId);
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.DELETE, url, new HashMap<>(), null,
                                                         true, JsonNode.class).orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(FOLLOWING).asBoolean();
  }

  @Override
  public TwitterList getList(final String listId) {
    String              url        = getUrlHelper().getListUrlV2() + "/" + listId;
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, "owner_id");
    parameters.put(LIST_FIELDS, ALL_LIST_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    return getRequestHelperV1().getRequestWithParameters(url, parameters, TwitterList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UserList getListMembers(final String listId) {
    String              url        = getUrlHelper().getAddListMemberUrl(listId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, PINNED_TWEET_ID);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    return getUsersRecursively(Integer.MAX_VALUE, url, parameters);
  }

  @Override
  public TwitterListList getUserOwnedLists(final String userId) {
    String              url        = getUrlHelper().getOwnedListUrl(userId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, "owner_id");
    parameters.put(LIST_FIELDS, ALL_LIST_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    return getRequestHelperV1().getRequestWithParameters(url, parameters, TwitterListList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public TweetList getListTweets(String listId, AdditionalParameters additionalParameters) {
    String              url        = getUrlHelper().getListTweetsUrl(listId);
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);

    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelperV2().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }

    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }

    return getTweetsRecursively(url, parameters, getRequestHelper());
  }

  @Override
  public Tweet postTweet(final String text) {
    return postTweet(TweetParameters.builder().text(text).build());
  }

  @SneakyThrows
  @Override
  public Tweet postTweet(final TweetParameters tweetParameters) {
    String url  = getUrlHelper().getPostTweetUrl();
    String body = JsonHelper.toJson(tweetParameters);
    return getRequestHelperV1().postRequestWithBodyJson(url, new HashMap<>(), body, TweetV2.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public boolean deleteTweet(final String tweetId) {
    String url = getUrlHelper().getPostTweetUrl() + "/" + tweetId;
    JsonNode jsonNode = getRequestHelperV1().makeRequest(Verb.DELETE, url, new HashMap<>(), null, true, JsonNode.class)
                                            .orElseThrow(NoSuchElementException::new);
    return jsonNode.get(DATA).get(DELETED).asBoolean();
  }

  @Override
  public DirectMessage getDirectMessageEvents() {
    return getDirectMessageEvents(AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public DirectMessage getDirectMessageEvents(final AdditionalParameters additionalParameters) {
    String              url        = getUrlHelper().getDmEventsUrl();
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(DM_FIELDS, ALL_DM_FIELDS);
    parameters.put(EXPANSION, ALL_DM_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return getRequestHelperV1().getRequestWithParameters(url, parameters, DirectMessage.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public DirectMessage getDirectMessagesByConversation(String conversationId) {
    return getDirectMessagesByConversation(conversationId, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public DirectMessage getDirectMessagesByConversation(String conversationId, final AdditionalParameters additionalParameters) {
    String              url        = getUrlHelper().getDmLookupUrl(conversationId);
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(DM_FIELDS, ALL_DM_FIELDS);
    parameters.put(EXPANSION, ALL_DM_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return getRequestHelperV1().getRequestWithParameters(url, parameters, DirectMessage.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public DirectMessage getDirectMessagesByUser(final String participantId) {
    return getDirectMessagesByUser(participantId, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public DirectMessage getDirectMessagesByUser(final String participantId, final AdditionalParameters additionalParameters) {
    String              url        = getUrlHelper().getDmUserLookupUrl(participantId);
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(DM_FIELDS, ALL_DM_FIELDS);
    parameters.put(EXPANSION, ALL_DM_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return getRequestHelperV1().getRequestWithParameters(url, parameters, DirectMessage.class).orElseThrow(NoSuchElementException::new);

  }

  @Override
  public PostDmResponse createDirectMessage(final String conversationId, String text) {
    return createDirectMessage(conversationId, DmMessage.builder().text(text).build());
  }

  @Override
  public PostDmResponse createDirectMessage(final String conversationId, DmMessage message) {
    String url = getUrlHelper().getPostConversationDmUrl(conversationId);
    String body;
    try {
      body = JsonHelper.toJson(message);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalArgumentException();
    }
    return getRequestHelperV1().postRequestWithBodyJson(url, null, body, PostDmResponse.class).orElseThrow(NoSuchElementException::new);
  }

  public PostDmResponse createGroupDmConversation(List<String> participantIds, String text) {
    return createGroupDmConversation(DmParameters.builder()
                                                 .participantIds(participantIds)
                                                 .message(DmMessage.builder().text(text).build())
                                                 .build());
  }

  public PostDmResponse createGroupDmConversation(DmParameters parameters) {
    String url = getUrlHelper().getCreateDmConversationUrl();
    String body;
    try {
      body = JsonHelper.toJson(parameters);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalArgumentException();
    }
    return getRequestHelperV1().postRequestWithBodyJson(url, null, body, PostDmResponse.class).orElseThrow(NoSuchElementException::new);
  }

  public PostDmResponse createUserDmConversation(String participantId, String text) {
    return createUserDmConversation(participantId, DmMessage.builder().text(text).build());
  }

  public PostDmResponse createUserDmConversation(String participantId, DmMessage message) {
    String url = getUrlHelper().getPostUserDmUrl(participantId);
    String body;
    try {
      body = JsonHelper.toJson(message);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalArgumentException();
    }
    return getRequestHelperV1().postRequestWithBodyJson(url, null, body, PostDmResponse.class).orElseThrow(NoSuchElementException::new);
  }


  @Override
  public Tweet getTweet(String tweetId) {
    String              url        = getUrlHelper().getTweetUrl(tweetId);
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return getRequestHelper().getRequestWithParameters(url, parameters, TweetV2.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public TweetList getTweets(List<String> tweetIds) {
    String              url        = getUrlHelper().getTweetsUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    StringBuilder result = new StringBuilder();
    int           i      = 0;
    while (i < tweetIds.size() && i < URLHelper.MAX_LOOKUP) {
      String id = tweetIds.get(i);
      result.append(id);
      result.append(",");
      i++;
    }
    result.delete(result.length() - 1, result.length());
    parameters.put("ids", result.toString());
    return getRequestHelper().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public boolean hideReply(final String tweetId, final boolean hide) {
    String url = getUrlHelper().getHideReplyUrl(tweetId);
    try {
      String body = JsonHelper.toJson(new HiddenData(hide));
      HiddenResponse response = requestHelperV1.putRequest(url, body, HiddenResponse.class)
                                               .orElseThrow(NoSuchElementException::new);
      return response.getData().isHidden();
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalArgumentException();
    }
  }

  @Override
  public TweetList searchTweets(String query) {
    return searchTweets(query, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public TweetList searchTweets(String query, AdditionalParameters additionalParameters) {
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(QUERY, query);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    String url = urlHelper.getSearchRecentTweetsUrl();
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelper().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }
    return getTweetsRecursively(url, parameters, getRequestHelper());
  }

  @Override
  public TweetList searchAllTweets(final String query) {
    return searchAllTweets(query, AdditionalParameters.builder().maxResults(500).build());
  }

  @Override
  public TweetList searchAllTweets(final String query, AdditionalParameters additionalParameters) {
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(QUERY, query);
    if (additionalParameters.getMaxResults() <= 100) {
      parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    } else {
      LOGGER.warn("Removing context_annotations from tweet_fields because max_result is greater 100");
      parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS.replace(",context_annotations", ""));
    }
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    String url = urlHelper.getSearchAllTweetsUrl();
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelperV2().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }
    return getTweetsRecursively(url, parameters, getRequestHelperV2());
  }

  /**
   * Call an endpoint related to tweets recursively until next_token is null to provide a full result
   */
  private TweetList getTweetsRecursively(String url, Map<String, String> parameters, AbstractRequestHelper requestHelper) {
    String    next;
    TweetList result   = TweetList.builder().data(new ArrayList<>()).meta(new TweetMeta()).build();
    String    newestId = null;
    do {
      Optional<TweetList> tweetList = requestHelper.getRequestWithParameters(url, parameters, TweetList.class);
      if (!tweetList.isPresent() || tweetList.get().getData() == null) {
        result.getMeta().setNextToken(null);
        break;
      }
      result.getData().addAll(tweetList.get().getData());
      if (newestId == null) {
        newestId = tweetList.get().getMeta().getNewestId();
      }
      TweetMeta meta = TweetMeta.builder()
                                .resultCount(result.getData().size())
                                .oldestId(tweetList.get().getMeta().getOldestId())
                                .newestId(newestId)
                                .nextToken(tweetList.get().getMeta().getNextToken())
                                .build();
      result.setMeta(meta);
      result.setIncludes(tweetList.get().getIncludes());
      next = tweetList.get().getMeta().getNextToken();
      if (url.contains("/search")) { // dirty
        parameters.put(AdditionalParameters.NEXT_TOKEN, next);
      } else {
        parameters.put(AdditionalParameters.PAGINATION_TOKEN, next);
      }
    } while (next != null);
    return result;
  }

  /**
   * Call an endpoint related to users recursively until next_token is null to provide a full result
   */
  private UserList getUsersRecursively(String url, Map<String, String> parameters, AbstractRequestHelper requestHelper) {
    String   next;
    UserList result = UserList.builder().data(new ArrayList<>()).meta(new UserMeta()).build();
    do {
      Optional<UserList> userList = requestHelper.getRequestWithParameters(url, parameters, UserList.class);
      if (!userList.isPresent() || userList.get().getData() == null) {
        result.getMeta().setNextToken(null);
        break;
      }
      result.getData().addAll(userList.get().getData());
      UserMeta meta = UserMeta.builder()
                              .resultCount(result.getData().size())
                              .nextToken(userList.get().getMeta().getNextToken())
                              .build();
      result.setMeta(meta);
      next = userList.get().getMeta().getNextToken();
      parameters.put(AdditionalParameters.PAGINATION_TOKEN, next);
    } while (next != null);
    return result;
  }

  @Deprecated
  @Override
  /**
   * Use {@link TwitterClient#searchTweets(query)} instead.
   */
  public List<Tweet> searchForTweetsWithin30days(String query, LocalDateTime fromDate, LocalDateTime toDate,
                                                 String envName) {
    int                 count      = 100;
    Map<String, String> parameters = new HashMap<>();
    parameters.put(QUERY, query);
    parameters.put("maxResults", String.valueOf(count));
    parameters.put("fromDate", ConverterHelper.getStringFromDate(fromDate));
    parameters.put("toDate", ConverterHelper.getStringFromDate(toDate));
    String      next;
    List<Tweet> result = new ArrayList<>();
    do {
      Optional<TweetSearchResponseV1> tweetSearchV1DTO = getRequestHelper().getRequestWithParameters(
          urlHelper.getSearchTweet30DaysUrl(envName), parameters, TweetSearchResponseV1.class);
      if (!tweetSearchV1DTO.isPresent() || tweetSearchV1DTO.get().getResults() == null) {
        break;
      }
      result.addAll(tweetSearchV1DTO.get().getResults());
      next = tweetSearchV1DTO.get().getNext();
      parameters.put(NEXT, next);
    } while (next != null);
    return result;
  }

  @Override
  @Deprecated
  /**
   * Use {@link TwitterClient#searchAllTweets(String)} (query)} instead.
   */
  public List<Tweet> searchForTweetsArchive(String query, LocalDateTime fromDate, LocalDateTime toDate,
                                            String envName) {
    int                 count      = 100;
    Map<String, String> parameters = new HashMap<>();
    parameters.put(QUERY, query);
    parameters.put(MAX_RESULTS, String.valueOf(count));
    parameters.put("fromDate", ConverterHelper.getStringFromDate(fromDate));
    parameters.put("toDate", ConverterHelper.getStringFromDate(toDate));
    String      next;
    List<Tweet> result = new ArrayList<>();
    do {
      Optional<TweetSearchResponseV1> tweetSearchV1DTO = getRequestHelper().getRequestWithParameters(
          urlHelper.getSearchTweetFullArchiveUrl(envName), parameters, TweetSearchResponseV1.class);
      if (!tweetSearchV1DTO.isPresent()) {
        LOGGER.error("Empty response on searchForTweetsArchive");
        break;
      }
      result.addAll(tweetSearchV1DTO.get().getResults());
      next = tweetSearchV1DTO.get().getNext();
      parameters.put(NEXT, next);
    } while (next != null);
    return result;
  }

  @Override
  public Future<Response> startFilteredStream(Consumer<Tweet> consumer) {
    String              url        = urlHelper.getFilteredStreamUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return requestHelperV2.getAsyncRequest(url, parameters, consumer);
  }

  @Override
  public Future<Response> startFilteredStream(IAPIEventListener listener) {
    return startFilteredStream(listener, 0);
  }

  @Override
  public Future<Response> startFilteredStream(IAPIEventListener listener, int backfillMinutes) {
    String              url        = urlHelper.getFilteredStreamUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    if (backfillMinutes > 0) {
      parameters.put(BACKFILL_MINUTES, String.valueOf(backfillMinutes));
    }
    return requestHelperV2.getAsyncRequest(url, parameters, listener);
  }

  @Override
  public boolean stopFilteredStream(Future<Response> responseFuture, long timeout, TimeUnit unit) {
    try {
      Response response;
      if (timeout > 0 && unit != null) {
        response = responseFuture.get(timeout, unit);
      } else {
        response = responseFuture.get();
      }

      if (response == null) {
        return false;
      }
      response.getStream().close();
      return true;
    } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
      LOGGER.error("Couldn't stopFilteredstream ", e);
      Thread.currentThread().interrupt();
    }
    return false;
  }

  @Override
  public boolean stopFilteredStream(Future<Response> responseFuture) {
    return stopFilteredStream(responseFuture, 0, null);
  }

  @Override
  public List<StreamRule> retrieveFilteredStreamRules() {
    String      url    = urlHelper.getFilteredStreamRulesUrl();
    StreamRules result = requestHelperV2.getRequest(url, StreamRules.class).orElseThrow(NoSuchElementException::new);
    return result.getData();
  }

  @Override
  public StreamRule addFilteredStreamRule(String value, String tag) {
    String     url  = urlHelper.getFilteredStreamRulesUrl();
    StreamRule rule = StreamRule.builder().value(value).tag(tag).build();
    try {
      String      body   = "{\"add\": [" + JsonHelper.toJson(rule) + "]}";
      StreamRules result = requestHelperV2.postRequest(url, body, StreamRules.class).orElseThrow(NoSuchElementException::new);
      if (result.getData() == null || result.getData().isEmpty()) {
        LOGGER.error("Could not add filtered stream rule. Rule maybe already exists.");
        throw new IllegalArgumentException();
      }
      return result.getData().get(0);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalArgumentException();
    }
  }

  @Override
  public StreamRule addFilteredStreamRule(FilteredStreamRulePredicate value, String tag) {
    return addFilteredStreamRule(value.toString(), tag);
  }

  @Override
  public StreamMeta deleteFilteredStreamRule(FilteredStreamRulePredicate ruleValue) {
    return deleteFilteredStreamRule(ruleValue.toString());
  }

  @Override
  public StreamMeta deleteFilteredStreamRule(String ruleValue) {
    String      url    = urlHelper.getFilteredStreamRulesUrl();
    String      body   = "{\"delete\": {\"values\": [\"" + ruleValue + "\"]}}";
    StreamRules result = requestHelperV2.postRequest(url, body, StreamRules.class).orElseThrow(NoSuchElementException::new);
    return result.getMeta();
  }

  @Override
  public StreamMeta deleteFilteredStreamRuleId(String ruleId) {
    String      url    = urlHelper.getFilteredStreamRulesUrl();
    String      body   = "{\"delete\": {\"ids\": [\"" + ruleId + "\"]}}";
    StreamRules result = requestHelperV2.postRequest(url, body, StreamRules.class).orElseThrow(NoSuchElementException::new);
    return result.getMeta();
  }

  @Override
  public Future<Response> startSampledStream(Consumer<Tweet> consumer) {
    String              url        = urlHelper.getSampledStreamUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    return requestHelperV2.getAsyncRequest(url, parameters, consumer);
  }

  @Override
  public Future<Response> startSampledStream(IAPIEventListener listener) {
    return startSampledStream(listener, 0);
  }

  @Override
  public Future<Response> startSampledStream(IAPIEventListener listener, int backfillMinutes) {
    String              url        = urlHelper.getSampledStreamUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    if (backfillMinutes > 0) {
      parameters.put(BACKFILL_MINUTES, String.valueOf(backfillMinutes));
    }
    return requestHelperV2.getAsyncRequest(url, parameters, listener);
  }

  @Override
  public TweetList getUserTimeline(final String userId) {
    return getUserTimeline(userId, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public TweetList getUserTimeline(String userId, AdditionalParameters additionalParameters) {
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    parameters.put(USER_FIELDS, ALL_USER_FIELDS);
    parameters.put(PLACE_FIELDS, ALL_PLACE_FIELDS);
    parameters.put(POLL_FIELDS, ALL_POLL_FIELDS);
    parameters.put(MEDIA_FIELD, ALL_MEDIA_FIELDS);
    parameters.put(EXPANSION, ALL_EXPANSIONS);
    String url = urlHelper.getUserTimelineUrl(userId);
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelperV2().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }
    return getTweetsRecursively(url, parameters, getRequestHelperV2());
  }

  @Override
  public TweetList getUserMentions(final String userId) {
    return getUserMentions(userId, AdditionalParameters.builder().maxResults(100).build());
  }

  @Override
  public TweetList getUserMentions(final String userId, AdditionalParameters additionalParameters) {
    Map<String, String> parameters = additionalParameters.getMapFromParameters();
    parameters.put(TWEET_FIELDS, ALL_TWEET_FIELDS);
    String url = urlHelper.getUserMentionsUrl(userId);
    if (!additionalParameters.isRecursiveCall()) {
      return getRequestHelperV2().getRequestWithParameters(url, parameters, TweetList.class).orElseThrow(NoSuchElementException::new);
    }
    if (additionalParameters.getMaxResults() <= 0) {
      parameters.put(MAX_RESULTS, String.valueOf(100));
    }
    return getTweetsRecursively(url, parameters, getRequestHelperV2());
  }

  @Override
  public List<TweetV1> readTwitterDataFile(File file) throws IOException {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(TweetV1.class, new TweetV1Deserializer());
    ObjectMapper customObjectMapper = new ObjectMapper();
    customObjectMapper.registerModule(module);
    customObjectMapper.findAndRegisterModules();

    List<TweetV1> result = new ArrayList<>();
    if (!file.exists()) {
      LOGGER.error("File not found at : {}", file.toURI());
    } else {
      result = Arrays.asList(customObjectMapper.readValue(file, TweetV1[].class));
    }
    return result;
  }

  @Override
  public String getBearerToken() {
    return requestHelperV2.getBearerToken();
  }

  @Override
  public BearerToken getOAuth2RefreshToken(String refreshToken, String clientId) {
    String              url     = URLHelper.ACCESS_TOKEN_URL;
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
    Map<String, String> params = new HashMap<>();
    params.put("refresh_token", refreshToken);
    params.put("client_id", clientId);
    params.put("grant_type", "refresh_token");
    return requestHelperV2.makeRequest(Verb.POST, url, headers, params, null, false, BearerToken.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public BearerToken getOAuth2AccessToken(String clientId, String code, String codeVerifier, String redirectUri) {
    String              url    = URLHelper.ACCESS_TOKEN_URL;
    Map<String, String> params = new HashMap<>();
    params.put("client_id", clientId);
    params.put("code", code);
    params.put("redirect_uri", redirectUri);
    params.put("code_verifier", codeVerifier);
    params.put("grant_type", "authorization_code");
    return requestHelperV2.makeRequest(Verb.POST, url, null, params, null, false, BearerToken.class).orElseThrow(NoSuchElementException::new);
  }

  @Override

  public RequestToken getOauth1Token() {
    return getOauth1Token(null);
  }

  @Override
  public RequestToken getOauth1Token(String oauthCallback) {
    String              url        = URLHelper.GET_OAUTH1_TOKEN_URL;
    Map<String, String> parameters = new HashMap<>();
    if (oauthCallback != null) {
      parameters.put("oauth_callback", oauthCallback);
    }
    String       stringResponse = requestHelperV1.postRequest(url, parameters, String.class).orElseThrow(NoSuchElementException::new);
    RequestToken requestToken   = new RequestToken(stringResponse);
    LOGGER.info("Open the following URL to grant access to your account:");
    LOGGER.info("https://twitter.com/oauth/authenticate?oauth_token={}", requestToken.getOauthToken());
    return requestToken;
  }

  @Override
  public RequestToken getOAuth1AccessToken(RequestToken requestToken, String pinCode) {
    String              url        = URLHelper.GET_OAUTH1_ACCESS_TOKEN_URL;
    Map<String, String> parameters = new HashMap<>();
    parameters.put("oauth_verifier", pinCode);
    parameters.put("oauth_token", requestToken.getOauthToken());
    String stringResponse = requestHelperV1.postRequestWithoutSign(url, parameters, String.class).orElseThrow(NoSuchElementException::new);
    return new RequestToken(stringResponse);
  }

  @Override
  public UploadMediaResponse uploadMedia(String mediaName, byte[] data, MediaCategory mediaCategory) {
    String url = urlHelper.getUploadMediaUrl(mediaCategory);
    return requestHelperV1.uploadMedia(url, mediaName, data, UploadMediaResponse.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public UploadMediaResponse uploadMedia(File imageFile, MediaCategory mediaCategory) {
    String url = urlHelper.getUploadMediaUrl(mediaCategory);
    return requestHelperV1.uploadMedia(url, imageFile, UploadMediaResponse.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public CollectionsResponse collectionsCreate(String name, String description, String collectionUrl, TimeLineOrder timeLineOrder) {
    String              url        = getUrlHelper().getCollectionsCreateUrl();
    Map<String, String> parameters = new HashMap<>();
    parameters.put("name", name);
    parameters.put("description", description);
    parameters.put("url", collectionUrl);
    if (timeLineOrder != null) {
      parameters.put("timeline_order", timeLineOrder.value());
    }
    return requestHelperV1.postRequest(url, parameters, CollectionsResponse.class).orElseThrow(NoSuchElementException::new);
  }

  @Override
  public CollectionsResponse collectionsCurate(String collectionId, List<String> tweetIds) {
    String url = getUrlHelper().getCollectionsCurateUrl();

    // Can only curate 100 tweets at a time - so chunk if tweetIds is larger
    AtomicInteger index = new AtomicInteger(0);
    Stream<List<String>> chunked = tweetIds.stream()
                                           .collect(Collectors.groupingBy(x -> index.getAndIncrement() / URLHelper.MAX_LOOKUP))
                                           .entrySet().stream()
                                           .sorted(Map.Entry.comparingByKey())
                                           .map(Map.Entry::getValue);
    return chunked
        .map(
            chunk -> {
              String json = String.format("{\"id\": \"%s\",\"changes\": [", collectionId);
              json += chunk
                  .stream()
                  .map(tweetId -> String.format("{ \"op\": \"add\", \"tweet_id\": \"%s\"}", tweetId))
                  .collect(Collectors.joining(", "));
              json += "]}";
              return requestHelperV1.postRequestWithBodyJson(url, Collections.emptyMap(), json, CollectionsResponse.class)
                                    .orElseThrow(NoSuchElementException::new);
            })
        .filter(CollectionsResponse::hasErrors) // any errors? If so return first chunk of errors
        .findFirst()
        .orElse(new CollectionsResponse()); // success - no errors
  }

  @Override
  public CollectionsResponse collectionsDestroy(String collectionId) {
    String url = getUrlHelper().getCollectionsDestroyUrl(collectionId);
    return requestHelperV1.postRequest(url, Collections.emptyMap(), CollectionsResponse.class).orElseThrow(NoSuchElementException::new);
  }

  @Deprecated
  @Override
  public List<io.github.redouane59.twitter.dto.dm.deprecatedV1.DirectMessage> getDmList() {
    return getDmList(Integer.MAX_VALUE);
  }

  @Override
  public List<io.github.redouane59.twitter.dto.dm.deprecatedV1.DirectMessage> getDmList(int count) {
    List<io.github.redouane59.twitter.dto.dm.deprecatedV1.DirectMessage> result   = new ArrayList<>();
    int                                                                  maxCount = 50;
    String                                                               url      = getUrlHelper().getDMListUrl(maxCount);
    DmListAnswer                                                         dmListAnswer;
    do {
      dmListAnswer = requestHelperV1.getRequest(url, DmListAnswer.class).orElseThrow(NoSuchElementException::new);
      result.addAll(dmListAnswer.getDirectMessages());
      url = getUrlHelper().getDMListUrl(maxCount) + "&" + CURSOR + "=" + dmListAnswer.getNextCursor();
    }
    while (dmListAnswer.getNextCursor() != null && result.size() < count);
    return result.subList(0, Math.min(count, result.size())); // to fix the API bug which is not giving the right count
  }

  @Override
  public io.github.redouane59.twitter.dto.dm.deprecatedV1.DirectMessage getDm(String dmId) {
    String url = urlHelper.getDmUrl(dmId);
    io.github.redouane59.twitter.dto.dm.deprecatedV1.DmEvent
        result =
        getRequestHelper().getRequest(url, io.github.redouane59.twitter.dto.dm.deprecatedV1.DmEvent.class).orElseThrow(NoSuchElementException::new);
    return result.getEvent();
  }

  @Override
  public io.github.redouane59.twitter.dto.dm.deprecatedV1.DmEvent postDm(final String text, final String userId) {
    String url = urlHelper.getPostConversationDmUrl();
    try {
      String body = JsonHelper.toJson(
          io.github.redouane59.twitter.dto.dm.deprecatedV1.DmEvent.builder()
                                                                  .event(new io.github.redouane59.twitter.dto.dm.deprecatedV1.DirectMessage(text,
                                                                                                                                            userId))
                                                                  .build());
      return getRequestHelperV1().postRequestWithBodyJson(url, null, body, io.github.redouane59.twitter.dto.dm.deprecatedV1.DmEvent.class)
                                 .orElseThrow(NoSuchElementException::new);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }

  @Override
  public CollectionsResponse collectionsEntries(final String collectionId, int count, String maxPosition, String minPosition) {
    String              url        = getUrlHelper().getCollectionsEntriesUrl(collectionId);
    Map<String, String> parameters = new HashMap<>();
    if (count > 0) {
      parameters.put("count", Integer.toString(count));
    }
    if (maxPosition != null) {
      parameters.put("max_position", maxPosition);
    }
    if (minPosition != null) {
      parameters.put("min_position", minPosition);
    }
    return getRequestHelper().getRequestWithParameters(url, parameters, CollectionsResponse.class).orElseThrow(NoSuchElementException::new);
  }

  private AbstractRequestHelper getRequestHelper() {
    if (requestHelperV1.getTwitterCredentials().getAccessToken() != null
        && requestHelperV1.getTwitterCredentials().getAccessTokenSecret() != null) {
      return requestHelperV1;
    } else {
      return requestHelperV2;
    }
  }

  public String getUserIdFromAccessToken() {
    String accessToken = twitterCredentials.getAccessToken();
    if (accessToken == null
        || accessToken.isEmpty()
        || !accessToken.contains("-")) {
      LOGGER.error("Access token null, empty or incorrect");
      throw new IllegalArgumentException();
    }
    return accessToken.substring(0, accessToken.indexOf("-"));
  }
}
