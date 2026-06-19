package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFriendActionOutcome;
import cool.muyucloud.netherlink.link.model.LinkFriendActionResult;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.model.LinkPresenceStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NetherLinkFriendsScreen extends Screen {
    private static final Component TITLE = Component.translatable("netherlink.friends.title");
    private static final Component FRIENDS_TAB_TITLE = Component.translatable("netherlink.friends.tab.friends");
    private static final Component REQUESTS_TAB_TITLE = Component.translatable("netherlink.friends.tab.requests");
    private static final int LIST_ROW_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_GAP = 4;
    private static final int FOOTER_HEIGHT = 58;

    private final Screen parent;
    private final boolean allowJoin;
    private ClientFriendService service;
    private ClientFriendService.Snapshot snapshot = new ClientFriendService.Snapshot(List.of(), List.of(), List.of());
    private TabManager tabManager;
    private TabNavigationBar tabNavigation;
    private FriendsTab friendsTab;
    private RequestsTab requestsTab;
    private Component status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);

    public NetherLinkFriendsScreen(Screen parent, boolean allowJoin) {
        super(TITLE);
        this.parent = parent;
        this.allowJoin = allowJoin;
    }

    @Override
    protected void init() {
        this.service = new ClientFriendService(this.minecraft);
        this.friendsTab = new FriendsTab();
        this.requestsTab = new RequestsTab();
        this.tabManager = new TabManager(
            this::addRenderableWidget,
            this::removeWidget
        );
        this.tabNavigation = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(this.friendsTab, this.requestsTab)
            .build();
        this.addRenderableWidget(this.tabNavigation);
        this.tabNavigation.selectTab(0, false);
        this.repositionElements();
        this.refresh();
    }

    @Override
    protected void repositionElements() {
        if (this.tabNavigation == null || this.tabManager == null) {
            return;
        }
        this.tabNavigation.updateWidth(this.width);
        int top = this.tabNavigation.getRectangle().bottom();
        this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - top));
    }

    private void refresh() {
        this.status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);
        this.setRefreshActive(false);
        this.service.refresh().whenComplete((result, error) -> this.minecraft.execute(() -> {
            this.setRefreshActive(true);
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.error", LinkFailures.from(error).message()).withStyle(ChatFormatting.RED);
                return;
            }
            this.snapshot = result;
            this.friendsTab.setSnapshot(result);
            this.requestsTab.setSnapshot(result);
            int requestCount = result.incoming().size() + result.outgoing().size();
            this.status = Component.translatable("netherlink.friends.loaded", result.friends().size() + requestCount).withStyle(ChatFormatting.GRAY);
        }));
    }

    private void setRefreshActive(boolean active) {
        if (this.friendsTab != null) {
            this.friendsTab.refreshButton.active = active;
        }
        if (this.requestsTab != null) {
            this.requestsTab.refreshButton.active = active;
        }
    }

    private void runFriendAction(CompletableFuture<LinkFriendActionOutcome> action, String successKey) {
        this.status = Component.translatable("netherlink.friends.working").withStyle(ChatFormatting.GRAY);
        action.whenComplete((outcome, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.error", LinkFailures.from(error).message()).withStyle(ChatFormatting.RED);
            } else if (outcome.result() == LinkFriendActionResult.SUCCESS) {
                this.status = Component.translatable(successKey).withStyle(ChatFormatting.GREEN);
                this.refresh();
            } else {
                String detail = outcome.failure() != null ? outcome.failure().message() : outcome.result().name();
                this.status = Component.translatable("netherlink.friends.result", detail).withStyle(ChatFormatting.RED);
            }
        }));
    }

    private void join(ClientFriendService.Friend friend, ClientFriendService.Instance instance) {
        String presenceId = instance.presenceId();
        if (presenceId == null) {
            return;
        }
        this.status = Component.translatable("netherlink.friends.joining", friend.name()).withStyle(ChatFormatting.YELLOW);
        ClientJoinController.join(this.minecraft, friend.profileId(), presenceId).whenComplete((_, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.join_failed", LinkFailures.from(error).message()).withStyle(ChatFormatting.RED);
            } else {
                this.status = Component.translatable("netherlink.friends.join_sent", friend.name()).withStyle(ChatFormatting.GRAY);
            }
            this.friendsTab.updateButtons();
        }));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.tabManager != null && this.tabManager.getCurrentTab() == this.friendsTab && this.friendsTab.list.children().isEmpty()) {
            Component empty = this.friendsTab.viewedFriend == null
                ? Component.translatable("netherlink.friends.empty")
                : Component.translatable("netherlink.friends.instances.empty");
            graphics.centeredText(this.font, empty.copy().withStyle(ChatFormatting.GRAY), this.width / 2, this.friendsTab.list.getY() + 18, -1);
        } else if (this.tabManager != null && this.tabManager.getCurrentTab() == this.requestsTab && this.requestsTab.list.children().isEmpty()) {
            graphics.centeredText(
                this.font,
                Component.translatable("netherlink.friends.requests.empty").withStyle(ChatFormatting.GRAY),
                this.width / 2,
                this.requestsTab.list.getY() + 18,
                -1
            );
        }
        graphics.centeredText(this.font, this.status, this.width / 2, this.height - 49, -1);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static void positionButtons(List<Button> buttons, int screenWidth, int y) {
        int totalWidth = buttons.size() * BUTTON_WIDTH + Math.max(0, buttons.size() - 1) * BUTTON_GAP;
        int x = (screenWidth - totalWidth) / 2;
        for (Button button : buttons) {
            button.setPosition(x, y);
            x += BUTTON_WIDTH + BUTTON_GAP;
        }
    }

    private final class FriendsTab implements Tab {
        private final SelectionList list = new SelectionList(
            NetherLinkFriendsScreen.this.minecraft,
            _ -> this.updateButtons(),
            this::activate
        );
        private final StringWidget heading = new StringWidget(FRIENDS_TAB_TITLE, NetherLinkFriendsScreen.this.font);
        private final Button openButton = Button.builder(Component.translatable("netherlink.friends.open"), _ -> this.openSelected()).width(BUTTON_WIDTH).build();
        private final Button joinButton = Button.builder(Component.translatable("netherlink.friends.join"), _ -> this.joinSelected()).width(BUTTON_WIDTH).build();
        private final Button removeButton = Button.builder(Component.translatable("netherlink.friends.remove"), _ -> this.removeSelected()).width(BUTTON_WIDTH).build();
        private final Button backButton = Button.builder(CommonComponents.GUI_BACK, _ -> this.showFriends()).width(BUTTON_WIDTH).build();
        private final Button refreshButton = Button.builder(Component.translatable("netherlink.friends.refresh"), _ -> NetherLinkFriendsScreen.this.refresh()).width(BUTTON_WIDTH).build();
        private final Button doneButton = Button.builder(CommonComponents.GUI_DONE, _ -> NetherLinkFriendsScreen.this.onClose()).width(BUTTON_WIDTH).build();
        private ClientFriendService.@Nullable Friend viewedFriend;

        @Override
        public @NonNull Component getTabTitle() {
            return FRIENDS_TAB_TITLE;
        }

        @Override
        public @NonNull Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.heading);
            consumer.accept(this.list);
            consumer.accept(this.openButton);
            consumer.accept(this.joinButton);
            consumer.accept(this.removeButton);
            consumer.accept(this.backButton);
            consumer.accept(this.refreshButton);
            consumer.accept(this.doneButton);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            this.heading.setPosition((NetherLinkFriendsScreen.this.width - NetherLinkFriendsScreen.this.font.width(this.heading.getMessage())) / 2, area.top() + 7);
            int listTop = area.top() + 22;
            this.list.updateSizeAndPosition(NetherLinkFriendsScreen.this.width, NetherLinkFriendsScreen.this.height - FOOTER_HEIGHT - listTop, listTop);
            this.layoutFooter();
        }

        private void setSnapshot(ClientFriendService.Snapshot snapshot) {
            if (this.viewedFriend != null) {
                UUID profileId = this.viewedFriend.profileId();
                this.viewedFriend = snapshot.friends().stream().filter(friend -> friend.profileId().equals(profileId)).findFirst().orElse(null);
            }
            this.rebuild();
        }

        private void rebuild() {
            if (this.viewedFriend == null) {
                this.heading.setMessage(FRIENDS_TAB_TITLE);
                this.list.setRows(snapshot.friends().stream().map(friend -> (Row)new FriendRow(friend)).toList());
            } else {
                this.heading.setMessage(Component.translatable("netherlink.friends.instances.title", this.viewedFriend.name()));
                ClientFriendService.Friend friend = this.viewedFriend;
                this.list.setRows(friend.instances().stream().map(instance -> (Row)new InstanceRow(friend, instance)).toList());
            }
            this.layoutFooter();
            this.updateButtons();
        }

        private void activate(Row row) {
            if (row instanceof FriendRow) {
                this.openSelected();
            } else if (row instanceof InstanceRow) {
                this.joinSelected();
            }
        }

        private void openSelected() {
            if (this.list.getSelected() instanceof FriendRow row) {
                this.viewedFriend = row.friend;
                this.rebuild();
            }
        }

        private void showFriends() {
            this.viewedFriend = null;
            this.rebuild();
        }

        private void joinSelected() {
            if (this.list.getSelected() instanceof InstanceRow row) {
                NetherLinkFriendsScreen.this.join(row.friend, row.instance);
            }
        }

        private void removeSelected() {
            if (this.list.getSelected() instanceof FriendRow row) {
                NetherLinkFriendsScreen.this.runFriendAction(
                    NetherLinkFriendsScreen.this.service.remove(row.friend.profileId()),
                    "netherlink.friends.removed"
                );
            }
        }

        private void layoutFooter() {
            boolean details = this.viewedFriend != null;
            this.openButton.visible = !details;
            this.removeButton.visible = !details;
            this.joinButton.visible = details;
            this.backButton.visible = details;
            List<Button> buttons = details
                ? List.of(this.backButton, this.joinButton, this.refreshButton, this.doneButton)
                : List.of(this.openButton, this.removeButton, this.refreshButton, this.doneButton);
            positionButtons(buttons, NetherLinkFriendsScreen.this.width, NetherLinkFriendsScreen.this.height - 28);
        }

        private void updateButtons() {
            Row selected = this.list.getSelected();
            this.openButton.active = selected instanceof FriendRow;
            this.removeButton.active = selected instanceof FriendRow;
            this.joinButton.active = allowJoin
                && selected instanceof InstanceRow row
                && row.instance.joinable()
                && row.instance.presenceId() != null
                && !ClientJoinController.hasOutgoingJoin();
        }
    }

    private final class RequestsTab implements Tab {
        private final EditBox addName = new EditBox(NetherLinkFriendsScreen.this.font, 0, 0, 220, 20, Component.translatable("netherlink.friends.add"));
        private final Button addButton = Button.builder(Component.translatable("netherlink.friends.add"), _ -> this.addFriend()).width(86).build();
        private final SelectionList list = new SelectionList(NetherLinkFriendsScreen.this.minecraft, _ -> this.updateButtons(), _ -> this.activateSelected());
        private final Button acceptButton = Button.builder(Component.translatable("netherlink.friends.accept"), _ -> this.acceptSelected()).width(BUTTON_WIDTH).build();
        private final Button declineButton = Button.builder(Component.translatable("netherlink.friends.decline"), _ -> this.declineSelected()).width(BUTTON_WIDTH).build();
        private final Button refreshButton = Button.builder(Component.translatable("netherlink.friends.refresh"), _ -> NetherLinkFriendsScreen.this.refresh()).width(BUTTON_WIDTH).build();
        private final Button doneButton = Button.builder(CommonComponents.GUI_DONE, _ -> NetherLinkFriendsScreen.this.onClose()).width(BUTTON_WIDTH).build();

        private RequestsTab() {
            this.addName.setHint(Component.translatable("netherlink.friends.add.hint"));
            this.addName.setMaxLength(16);
        }

        @Override
        public @NonNull Component getTabTitle() {
            return REQUESTS_TAB_TITLE;
        }

        @Override
        public @NonNull Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.addName);
            consumer.accept(this.addButton);
            consumer.accept(this.list);
            consumer.accept(this.acceptButton);
            consumer.accept(this.declineButton);
            consumer.accept(this.refreshButton);
            consumer.accept(this.doneButton);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            int inputLeft = (NetherLinkFriendsScreen.this.width - 310) / 2;
            this.addName.setPosition(inputLeft, area.top() + 7);
            this.addButton.setPosition(inputLeft + 224, area.top() + 7);
            int listTop = area.top() + 34;
            this.list.updateSizeAndPosition(NetherLinkFriendsScreen.this.width, NetherLinkFriendsScreen.this.height - FOOTER_HEIGHT - listTop, listTop);
            positionButtons(
                List.of(this.acceptButton, this.declineButton, this.refreshButton, this.doneButton),
                NetherLinkFriendsScreen.this.width,
                NetherLinkFriendsScreen.this.height - 28
            );
        }

        private void setSnapshot(ClientFriendService.Snapshot snapshot) {
            List<Row> rows = new ArrayList<>();
            snapshot.incoming().forEach(request -> rows.add(new RequestRow(request)));
            snapshot.outgoing().forEach(request -> rows.add(new RequestRow(request)));
            this.list.setRows(rows);
            this.updateButtons();
        }

        private void addFriend() {
            String name = this.addName.getValue().trim();
            if (!name.isEmpty()) {
                this.addName.setValue("");
                NetherLinkFriendsScreen.this.runFriendAction(
                    NetherLinkFriendsScreen.this.service.add(name),
                    "netherlink.friends.added"
                );
            }
        }

        private void activateSelected() {
            if (this.list.getSelected() instanceof RequestRow row && row.request.relationship() == LinkFriendRelationship.INCOMING) {
                this.acceptSelected();
            }
        }

        private void acceptSelected() {
            if (this.list.getSelected() instanceof RequestRow row && row.request.relationship() == LinkFriendRelationship.INCOMING) {
                NetherLinkFriendsScreen.this.runFriendAction(
                    NetherLinkFriendsScreen.this.service.accept(row.request.profileId()),
                    "netherlink.friends.accepted"
                );
            }
        }

        private void declineSelected() {
            if (this.list.getSelected() instanceof RequestRow row) {
                CompletableFuture<LinkFriendActionOutcome> action = row.request.relationship() == LinkFriendRelationship.OUTGOING
                    ? NetherLinkFriendsScreen.this.service.revoke(row.request.profileId())
                    : NetherLinkFriendsScreen.this.service.decline(row.request.profileId());
                NetherLinkFriendsScreen.this.runFriendAction(action, "netherlink.friends.declined");
            }
        }

        private void updateButtons() {
            Row selected = this.list.getSelected();
            this.acceptButton.active = selected instanceof RequestRow row && row.request.relationship() == LinkFriendRelationship.INCOMING;
            this.declineButton.active = selected instanceof RequestRow;
            this.declineButton.setMessage(
                selected instanceof RequestRow row && row.request.relationship() == LinkFriendRelationship.OUTGOING
                    ? Component.translatable("netherlink.friends.revoke")
                    : Component.translatable("netherlink.friends.decline")
            );
        }
    }

    private static final class SelectionList extends ObjectSelectionList<Row> {
        private final Consumer<Row> selectionChanged;
        private final Consumer<Row> activated;

        private SelectionList(Minecraft minecraft, Consumer<Row> selectionChanged, Consumer<Row> activated) {
            super(minecraft, 0, 0, 0, LIST_ROW_HEIGHT);
            this.selectionChanged = selectionChanged;
            this.activated = activated;
        }

        private void setRows(List<Row> rows) {
            rows.forEach(row -> row.attach(this));
            this.replaceEntries(List.copyOf(rows));
            this.selectionChanged.accept(null);
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            this.selectionChanged.accept(row);
        }

        @Override
        public int getRowWidth() {
            return Math.clamp(this.getWidth() - 32, 220, 400);
        }

        private void activate(Row row) {
            this.activated.accept(row);
        }
    }

    private abstract static class Row extends ObjectSelectionList.Entry<Row> {
        private SelectionList owner;

        private void attach(SelectionList owner) {
            this.owner = owner;
        }

        protected abstract Component title();

        protected abstract Component description();

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            graphics.text(minecraft.font, this.title(), this.getContentX() + 4, this.getContentY() + 3, -1);
            graphics.text(minecraft.font, this.description(), this.getContentX() + 4, this.getContentY() + 17, 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            this.owner.setSelected(this);
            if (doubleClick) {
                this.owner.activate(this);
            }
            return true;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.empty().append(this.title()).append(Component.literal(". ")).append(this.description());
        }

    }

    private static final class FriendRow extends Row {
        private final ClientFriendService.Friend friend;

        private FriendRow(ClientFriendService.Friend friend) {
            this.friend = friend;
        }

        @Override
        protected Component title() {
            return Component.literal(this.friend.name());
        }

        @Override
        protected Component description() {
            return this.friend.instances().isEmpty()
                ? Component.translatable("netherlink.friends.status.offline")
                : Component.translatable("netherlink.friends.instances.count", this.friend.instances().size());
        }
    }

    private static final class InstanceRow extends Row {
        private final ClientFriendService.Friend friend;
        private final ClientFriendService.Instance instance;

        private InstanceRow(ClientFriendService.Friend friend, ClientFriendService.Instance instance) {
            this.friend = friend;
            this.instance = instance;
        }

        @Override
        protected Component title() {
            return this.instance.displayText().isBlank()
                ? Component.translatable("netherlink.friends.instance")
                : Component.literal(this.instance.displayText());
        }

        @Override
        protected Component description() {
            Component status = statusText(this.instance.status());
            return this.instance.joinable()
                ? Component.empty().append(status).append(Component.literal(" / ")).append(Component.translatable("netherlink.friends.status.joinable"))
                : status;
        }
    }

    private static final class RequestRow extends Row {
        private final ClientFriendService.Request request;

        private RequestRow(ClientFriendService.Request request) {
            this.request = request;
        }

        @Override
        protected Component title() {
            return Component.literal(this.request.name());
        }

        @Override
        protected Component description() {
            return Component.translatable(this.request.relationship() == LinkFriendRelationship.INCOMING
                ? "netherlink.friends.relation.incoming"
                : "netherlink.friends.relation.outgoing");
        }
    }

    private static Component statusText(LinkPresenceStatus status) {
        return switch (status) {
            case ONLINE -> Component.translatable("netherlink.friends.status.online");
            case PLAYING_OFFLINE -> Component.translatable("netherlink.friends.status.playing_offline");
            case PLAYING_REALMS -> Component.translatable("netherlink.friends.status.playing_realms");
            case PLAYING_SERVER -> Component.translatable("netherlink.friends.status.playing_server");
            case HOSTING -> Component.translatable("netherlink.friends.status.playing_hosted_server");
            case OFFLINE, UNKNOWN -> Component.translatable("netherlink.friends.status.offline");
        };
    }
}
