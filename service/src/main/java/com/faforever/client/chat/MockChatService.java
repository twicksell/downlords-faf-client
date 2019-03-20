package com.faforever.client.chat;

import com.faforever.client.SpringProfiles;
import com.faforever.client.i18n.I18n;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.user.LoginSuccessEvent;
import com.faforever.client.user.UserService;
import com.faforever.client.util.ConcurrentUtil;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.concurrent.Task;
import javafx.scene.paint.Color;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Lazy
@Service
@Profile(SpringProfiles.PROFILE_OFFLINE)
// NOSONAR
public class MockChatService implements ChatService {

  private static final int CHAT_MESSAGE_INTERVAL = 5000;
  private static final long CONNECTION_DELAY = 1000;
  private final Timer timer;
  private final Collection<Consumer<ChatMessage>> onChatMessageListeners;
  private final Map<String, Channel> channelUserListListeners;
  private final ObjectProperty<ConnectionState> connectionState;
  private final IntegerProperty unreadMessagesCount;

  private final UserService userService;
  private final TaskService taskService;
  private final I18n i18n;
  private final EventBus eventBus;

  public MockChatService(UserService userService, TaskService taskService, I18n i18n, EventBus eventBus) {
    connectionState = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
    unreadMessagesCount = new SimpleIntegerProperty();

    onChatMessageListeners = new ArrayList<>();
    channelUserListListeners = new HashMap<>();

    timer = new Timer(true);
    this.userService = userService;
    this.taskService = taskService;
    this.i18n = i18n;
    this.eventBus = eventBus;
  }

  @PostConstruct
  void postConstruct() {
    eventBus.register(this);
  }

  @Subscribe
  public void onLoginSuccessEvent(LoginSuccessEvent event) {
    connect();
  }

  private void simulateConnectionEstablished() {
    connectionState.set(ConnectionState.CONNECTED);
    joinChannel("#mockChannel");
  }

  @Override
  public void connect() {
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        simulateConnectionEstablished();
      }
    }, CONNECTION_DELAY);
  }

  @Override
  public void disconnect() {
    timer.cancel();
  }

  @Override
  public CompletableFuture<String> sendMessageInBackground(String target, String message) {
    return taskService.submitTask(new CompletableTask<String>(Priority.HIGH) {
      @Override
      protected String call() throws Exception {
        updateTitle(i18n.get("chat.sendMessageTask.title"));

        Thread.sleep(200);
        return message;
      }
    }).getFuture();
  }

  @Override
  public Channel getOrCreateChannel(String channelName) {
    channelUserListListeners.putIfAbsent(channelName, new Channel(channelName));
    return channelUserListListeners.get(channelName);
  }

  @Override
  public ChatChannelUser getOrCreateChatUser(String username, String channel, boolean isModerator) {
    return null;
  }

  @Override
  public void addUsersListener(String channelName, MapChangeListener<String, ChatChannelUser> listener) {
    getOrCreateChannel(channelName).addUsersListeners(listener);
  }

  @Override
  public void addChatUsersByNameListener(MapChangeListener<String, ChatChannelUser> listener) {

  }

  @Override
  public void addChannelsListener(MapChangeListener<String, Channel> listener) {

  }

  @Override
  public void removeUsersListener(String channelName, MapChangeListener<String, ChatChannelUser> listener) {

  }

  @Override
  public void leaveChannel(String channelName) {

  }

  @Override
  public CompletableFuture<String> sendActionInBackground(String target, String action) {
    return sendMessageInBackground(target, action);
  }

  @Override
  public void joinChannel(String channelName) {
    ConcurrentUtil.executeInBackground(new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        ChatChannelUser chatUser = new ChatChannelUser(userService.getDisplayName(), null, false);
        ChatChannelUser mockUser = new ChatChannelUser("MockUser", null, false);
        ChatChannelUser moderatorUser = new ChatChannelUser("MockModerator", null, true);

        Channel channel = getOrCreateChannel(channelName);
        channel.addUser(chatUser);
        channel.addUser(mockUser);
        channel.addUser(moderatorUser);
        channel.setTopic("le wild channel topic appears");

        return null;
      }
    });

    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        for (Consumer<ChatMessage> onChatMessageListener : onChatMessageListeners) {
          ChatMessage chatMessage = new ChatMessage(channelName, Instant.now(), "Mock User",
              String.format(
                  "%1$s Lorem ipsum dolor sit amet, consetetur %1$s sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam %1$s " +
                      "http://www.faforever.com/wp-content/uploads/2013/07/cropped-backForum41.jpg",
                  userService.getDisplayName()
              )
          );

          onChatMessageListener.accept(chatMessage);
        }
      }
    }, 0, CHAT_MESSAGE_INTERVAL);
  }

  @Override
  public boolean isDefaultChannel(String channelName) {
    return true;
  }

  @Override
  public void close() {

  }

  @Override
  public ObjectProperty<ConnectionState> connectionStateProperty() {
    return connectionState;
  }

  @Override
  public void reconnect() {

  }

  @Override
  public void whois(String username) {

  }

  @Override
  public void incrementUnreadMessagesCount(int delta) {
    synchronized (unreadMessagesCount) {
      unreadMessagesCount.set(unreadMessagesCount.get() + delta);
    }
  }

  @Override
  public ReadOnlyIntegerProperty unreadMessagesCount() {
    return unreadMessagesCount;
  }

  @Override
  public ChatChannelUser getChatUser(String username, String channelName) {
    return new ChatChannelUser(username, Color.ALICEBLUE, false);
  }

  @Override
  public String getDefaultChannelName() {
    return channelUserListListeners.keySet().iterator().next();
  }
}