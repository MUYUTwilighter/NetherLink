package cool.muyucloud.netherlink.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NetherLinkFriendsScreen extends Screen {
    private static final Component TITLE = Component.translatable("netherlink.friends.title");
    private static final Component FRIENDS_TAB_TITLE = Component.translatable("netherlink.friends.tab.friends");
    private static final Component REQUESTS_TAB_TITLE = Component.translatable("netherlink.friends.tab.requests");
    private static final Component SETTINGS_TAB_TITLE = Component.translatable("netherlink.friends.tab.settings");
    private static final int LIST_ROW_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_GAP = 4;
    private static final int FOOTER_HEIGHT = 58;
    private static final int TAB_COUNT = 3;
    private static final int TAB_MAX_WIDTH = 400;
    private static final int TAB_MARGIN = 14;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int SELECTED_TAB_COLOR = 0xFF000000;
    private static final int INACTIVE_TAB_COLOR = 0xFF202020;
    private static final int SETTINGS_CONTROL_WIDTH = 220;
    private static final int SETTINGS_RENDERER_HEIGHT = 78;
    private static final int SETTINGS_SCROLL_STEP = 18;
    private static final int SETTINGS_SCROLLBAR_WIDTH = 4;

    private final Screen parent;
    private final boolean allowJoin;
    private ClientFriendService service;
    private ClientFriendService.Snapshot snapshot = new ClientFriendService.Snapshot(List.of(), List.of(), List.of());
    private TabManager tabManager;
    private TabNavigationBar tabNavigation;
    private FriendsTab friendsTab;
    private RequestsTab requestsTab;
    private SettingsTab settingsTab;
    private @Nullable SelectionList activeList;
    private Component status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);

    public NetherLinkFriendsScreen(Screen parent, boolean allowJoin) {
        super(TITLE);
        this.parent = parent;
        this.allowJoin = allowJoin;
    }

    @Override
    protected void init() {
        ClientLinkSettings.applyConfiguredService(this.minecraft);
        this.service = new ClientFriendService(this.minecraft);
        this.friendsTab = new FriendsTab();
        this.requestsTab = new RequestsTab();
        this.settingsTab = new SettingsTab();
        this.tabManager = new TabManager(
            this::addRenderableWidget,
            this::removeWidget
        );
        this.tabNavigation = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(this.friendsTab, this.requestsTab, this.settingsTab)
            .build();
        this.addWidget(this.tabNavigation);
        this.tabNavigation.selectTab(0, false);
        this.repositionElements();
        this.refresh();
    }

    @Override
    protected void repositionElements() {
        if (this.tabNavigation == null || this.tabManager == null) {
            return;
        }
        this.tabNavigation.setWidth(this.width);
        this.tabNavigation.arrangeElements();
        int top = this.tabNavigation.getRectangle().bottom();
        this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - top));
    }

    private void refresh() {
        this.status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);
        this.setRefreshActive(false);
        this.service.refresh().whenComplete((result, error) -> this.minecraft.execute(() -> {
            this.setRefreshActive(true);
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.error", failureText(LinkFailures.from(error))).withStyle(ChatFormatting.RED);
                return;
            }
            this.snapshot = result;
            this.friendsTab.setSnapshot(result);
            this.requestsTab.setSnapshot(result);
            this.status = Component.translatable("netherlink.friends.loaded", result.friends().size()).withStyle(ChatFormatting.GRAY);
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
                this.status = Component.translatable("netherlink.friends.error", failureText(LinkFailures.from(error))).withStyle(ChatFormatting.RED);
            } else if (outcome.result() == LinkFriendActionResult.SUCCESS) {
                this.status = Component.translatable(successKey).withStyle(ChatFormatting.GREEN);
                this.refresh();
            } else {
                Component detail = outcome.failure() != null ? failureText(outcome.failure()) : actionResultText(outcome.result());
                this.status = Component.translatable("netherlink.friends.result", detail).withStyle(ChatFormatting.RED);
            }
        }));
    }

    private void join(ClientFriendService.Friend friend, ClientFriendService.Instance instance) {
        this.join(friend.profileId(), friend.name(), instance);
    }

    private void join(UUID profileId, String name, ClientFriendService.Instance instance) {
        String presenceId = instance.presenceId();
        if (presenceId == null) {
            return;
        }
        this.status = Component.translatable("netherlink.friends.joining", name).withStyle(ChatFormatting.YELLOW);
        this.minecraft.setScreen(new NetherLinkJoinScreen(this, profileId, presenceId, name));
    }

    private void activateList(@Nullable SelectionList list) {
        if (this.activeList == list) {
            return;
        }
        if (this.activeList != null) {
            this.removeWidget(this.activeList);
        }
        this.activeList = list;
        if (list != null) {
            this.addRenderableWidget(list);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderChromeForeground(graphics);
        if (this.tabManager != null && this.tabManager.getCurrentTab() == this.friendsTab && this.friendsTab.list.children().isEmpty()) {
            graphics.drawCenteredString(this.font, this.friendsTab.emptyMessage().copy().withStyle(ChatFormatting.GRAY), this.width / 2, this.friendsTab.list.top() + 18, -1);
        } else if (this.tabManager != null && this.tabManager.getCurrentTab() == this.requestsTab && this.requestsTab.list.children().isEmpty()) {
            graphics.drawCenteredString(
                this.font,
                Component.translatable("netherlink.friends.requests.empty").withStyle(ChatFormatting.GRAY),
                this.width / 2,
                this.requestsTab.list.top() + 18,
                -1
            );
        }
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 49, -1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.tabManager != null
            && this.settingsTab != null
            && this.tabManager.getCurrentTab() == this.settingsTab
            && this.settingsTab.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void renderTabBar(GuiGraphics graphics) {
        if (this.tabNavigation == null || this.tabManager == null) {
            return;
        }
        int totalWidth = tabTotalWidth();
        if (totalWidth <= 0) {
            return;
        }
        int x = tabLeft(totalWidth);
        int tabWidth = tabWidth(totalWidth);
        int tabHeight = Math.max(24, this.tabNavigation.getRectangle().height());
        for (int i = 0; i < TAB_COUNT; i++) {
            boolean selected = this.tabIndex() == i;
            int left = x + i * tabWidth;
            int right = i == TAB_COUNT - 1 ? x + totalWidth : left + tabWidth;
            graphics.fill(left, 0, right, tabHeight, BORDER_COLOR);
            graphics.fill(left + 1, 1, right - 1, tabHeight - 1, selected ? SELECTED_TAB_COLOR : INACTIVE_TAB_COLOR);
            if (selected) {
                graphics.fill(left + 1, tabHeight - 1, right - 1, tabHeight + 1, SELECTED_TAB_COLOR);
            }
            Component title = switch (i) {
                case 1 -> REQUESTS_TAB_TITLE;
                case 2 -> SETTINGS_TAB_TITLE;
                default -> FRIENDS_TAB_TITLE;
            };
            graphics.drawCenteredString(this.font, title, (left + right) / 2, (tabHeight - 8) / 2, selected ? 0xFFFFFFFF : 0xFFBBBBBB);
        }
    }

    private void renderChromeForeground(GuiGraphics graphics) {
        if (this.tabNavigation == null) {
            return;
        }
        this.renderTabBar(graphics);
        int tabBottom = this.tabNavigation.getRectangle().bottom();
        graphics.fill(0, tabBottom - 1, this.width, tabBottom, BORDER_COLOR);
        if (this.activeList != null) {
            graphics.fill(0, this.activeList.top() - 1, this.width, this.activeList.top(), BORDER_COLOR);
        }
        int footerTop = this.height - FOOTER_HEIGHT;
        graphics.fill(0, footerTop, this.width, footerTop + 1, BORDER_COLOR);
        if (this.tabManager != null && this.tabManager.getCurrentTab() == this.settingsTab) {
            this.settingsTab.renderScrollBar(graphics);
        }
    }

    private int tabIndex() {
        if (this.tabManager == null) {
            return 0;
        }
        Tab current = this.tabManager.getCurrentTab();
        if (current == this.requestsTab) {
            return 1;
        }
        if (current == this.settingsTab) {
            return 2;
        }
        return 0;
    }

    private int tabTotalWidth() {
        return Math.max(0, Math.min(TAB_MAX_WIDTH, this.width) - TAB_MARGIN * 2);
    }

    private static int tabWidth(int totalWidth) {
        return Mth.roundToward(totalWidth / TAB_COUNT, 2);
    }

    private int tabLeft(int totalWidth) {
        return Mth.roundToward((this.width - totalWidth) / 2, 2);
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

    private static void centerWidget(AbstractWidget widget, int centerX, int y) {
        widget.setPosition(centerX - widget.getWidth() / 2, y);
    }

    private final class FriendsTab implements Tab {
        private final SelectionList list = new SelectionList(
            NetherLinkFriendsScreen.this.minecraft,
            ignored4 -> this.updateButtons(),
            this::activate
        );
        private final StringWidget heading = new StringWidget(FRIENDS_TAB_TITLE, NetherLinkFriendsScreen.this.font);
        private final Button openButton = Button.builder(Component.translatable("netherlink.friends.open"), ignored5 -> this.openSelected()).width(BUTTON_WIDTH).build();
        private final Button joinButton = Button.builder(Component.translatable("netherlink.friends.join"), ignored6 -> this.joinSelected()).width(BUTTON_WIDTH).build();
        private final Button removeButton = Button.builder(Component.translatable("netherlink.friends.remove"), ignored7 -> this.removeSelected()).width(BUTTON_WIDTH).build();
        private final Button backButton = Button.builder(CommonComponents.GUI_BACK, ignored8 -> this.showFriends()).width(BUTTON_WIDTH).build();
        private final Button refreshButton = Button.builder(Component.translatable("netherlink.friends.refresh"), ignored9 -> NetherLinkFriendsScreen.this.refresh()).width(BUTTON_WIDTH).build();
        private final Button doneButton = Button.builder(CommonComponents.GUI_DONE, ignored10 -> NetherLinkFriendsScreen.this.onClose()).width(BUTTON_WIDTH).build();
        private ClientFriendService.@Nullable Friend viewedFriend;
        private boolean viewingSelf;

        @Override
        public @NotNull Component getTabTitle() {
            return FRIENDS_TAB_TITLE;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.heading);
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
            NetherLinkFriendsScreen.this.activateList(this.list);
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
            if (this.viewingSelf) {
                this.heading.setMessage(selfPresenceTitle(this.selfName()));
                this.list.setRows(snapshot.selfInstances().stream().map(instance -> (Row)new SelfInstanceRow(instance)).toList());
            } else if (this.viewedFriend == null) {
                this.heading.setMessage(FRIENDS_TAB_TITLE);
                List<Row> rows = new ArrayList<>();
                if (!snapshot.selfInstances().isEmpty() && snapshot.selfProfileId() != null) {
                    rows.add(new SelfPresenceRow(snapshot.selfProfileId(), this.selfName(), snapshot.selfInstances()));
                }
                snapshot.friends().stream().map(friend -> (Row)new FriendRow(friend)).forEach(rows::add);
                this.list.setRows(rows);
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
            } else if (row instanceof SelfPresenceRow) {
                this.openSelected();
            } else if (row instanceof InstanceRow || row instanceof SelfInstanceRow) {
                this.joinSelected();
            }
        }

        private void openSelected() {
            if (this.list.getSelected() instanceof SelfPresenceRow) {
                this.viewingSelf = true;
                this.viewedFriend = null;
                this.rebuild();
            } else if (this.list.getSelected() instanceof FriendRow row) {
                this.viewingSelf = false;
                this.viewedFriend = row.friend;
                this.rebuild();
            }
        }

        private void showFriends() {
            this.viewingSelf = false;
            this.viewedFriend = null;
            this.rebuild();
        }

        private void joinSelected() {
            if (this.list.getSelected() instanceof InstanceRow row) {
                NetherLinkFriendsScreen.this.join(row.friend, row.instance);
            } else if (this.list.getSelected() instanceof SelfInstanceRow row && snapshot.selfProfileId() != null) {
                String name = snapshot.selfName().isBlank()
                    ? NetherLinkFriendsScreen.this.minecraft.getUser().getName()
                    : snapshot.selfName();
                NetherLinkFriendsScreen.this.join(snapshot.selfProfileId(), name, row.instance);
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
            boolean details = this.viewingSelf || this.viewedFriend != null;
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
            this.openButton.active = selected instanceof FriendRow || selected instanceof SelfPresenceRow;
            this.removeButton.active = selected instanceof FriendRow;
            this.joinButton.active = allowJoin && this.canJoin(selected) && !ClientJoinController.hasOutgoingJoin();
        }

        private boolean canJoin(@Nullable Row selected) {
            if (selected instanceof InstanceRow row) {
                return row.instance.joinable() && row.instance.presenceId() != null;
            }
            if (selected instanceof SelfInstanceRow row) {
                return snapshot.selfProfileId() != null && row.instance.joinable() && row.instance.presenceId() != null;
            }
            return false;
        }

        private Component emptyMessage() {
            if (this.viewingSelf) {
                return Component.translatable("netherlink.friends.self.instances.empty");
            }
            return this.viewedFriend == null
                ? Component.translatable("netherlink.friends.empty")
                : Component.translatable("netherlink.friends.instances.empty");
        }

        private String selfName() {
            return snapshot.selfName().isBlank()
                ? NetherLinkFriendsScreen.this.minecraft.getUser().getName()
                : snapshot.selfName();
        }
    }

    private final class RequestsTab implements Tab {
        private final EditBox addName = new EditBox(NetherLinkFriendsScreen.this.font, 0, 0, 220, 20, Component.translatable("netherlink.friends.add"));
        private final Button addButton = Button.builder(Component.translatable("netherlink.friends.add"), ignored11 -> this.addFriend()).width(86).build();
        private final SelectionList list = new SelectionList(NetherLinkFriendsScreen.this.minecraft, ignored12 -> this.updateButtons(), ignored13 -> this.activateSelected());
        private final Button acceptButton = Button.builder(Component.translatable("netherlink.friends.accept"), ignored14 -> this.acceptSelected()).width(BUTTON_WIDTH).build();
        private final Button declineButton = Button.builder(Component.translatable("netherlink.friends.decline"), ignored15 -> this.declineSelected()).width(BUTTON_WIDTH).build();
        private final Button refreshButton = Button.builder(Component.translatable("netherlink.friends.refresh"), ignored16 -> NetherLinkFriendsScreen.this.refresh()).width(BUTTON_WIDTH).build();
        private final Button doneButton = Button.builder(CommonComponents.GUI_DONE, ignored17 -> NetherLinkFriendsScreen.this.onClose()).width(BUTTON_WIDTH).build();

        private RequestsTab() {
            this.addName.setHint(Component.translatable("netherlink.friends.add.hint"));
            this.addName.setMaxLength(16);
            this.addName.setBordered(true);
        }

        @Override
        public @NotNull Component getTabTitle() {
            return REQUESTS_TAB_TITLE;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.addName);
            consumer.accept(this.addButton);
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
            NetherLinkFriendsScreen.this.activateList(this.list);
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

    private final class SettingsTab implements Tab {
        private final StringWidget apiSettingsLabel = new StringWidget(Component.translatable("netherlink.friends.settings.api_instance"), NetherLinkFriendsScreen.this.font);
        private final Button apiButton = Button.builder(Component.empty(), ignored18 -> this.cycleApi()).width(220).build();
        private final StringWidget instanceNameLabel = new StringWidget(Component.translatable("netherlink.friends.settings.instance_name"), NetherLinkFriendsScreen.this.font);
        private final EditBox instanceName = new EditBox(NetherLinkFriendsScreen.this.font, 0, 0, 220, 20, Component.translatable("netherlink.friends.settings.instance_name"));
        private final Button applyButton = Button.builder(Component.translatable("netherlink.friends.settings.apply"), ignored19 -> this.apply()).width(BUTTON_WIDTH).build();
        private final Button doneButton = Button.builder(CommonComponents.GUI_DONE, ignored20 -> NetherLinkFriendsScreen.this.onClose()).width(BUTTON_WIDTH).build();
        private ResourceLocation selectedServiceId = LinkServices.current().id();
        private String savedInstanceName = ClientLinkSettings.configuredInstanceName(NetherLinkFriendsScreen.this.minecraft);
        private @Nullable LinkSettingsRenderer renderer;
        private boolean applying;
        private int scrollTop;
        private int scrollBottom;
        private int contentHeight;
        private int scrollOffset;

        private SettingsTab() {
            this.instanceName.setHint(Component.translatable("netherlink.friends.settings.instance_name.hint"));
            this.instanceName.setValue(this.savedInstanceName);
            this.instanceName.setResponder(ignored -> this.updateButtons());
            this.rebuildRenderer();
            this.updateButtons();
        }

        @Override
        public @NotNull Component getTabTitle() {
            return SETTINGS_TAB_TITLE;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.instanceNameLabel);
            consumer.accept(this.instanceName);
            consumer.accept(this.apiButton);
            consumer.accept(this.apiSettingsLabel);
            if (this.renderer != null) {
                this.renderer.visitChildren(consumer);
            }
            consumer.accept(this.applyButton);
            consumer.accept(this.doneButton);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            NetherLinkFriendsScreen.this.activateList(null);
            int center = NetherLinkFriendsScreen.this.width / 2;
            int footerTop = NetherLinkFriendsScreen.this.height - FOOTER_HEIGHT;
            this.scrollTop = area.top();
            this.scrollBottom = Math.max(this.scrollTop, footerTop);
            this.contentHeight = 116 + SETTINGS_RENDERER_HEIGHT;
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll());
            this.updateButtons();
            int contentTop = this.scrollTop - this.scrollOffset;
            this.instanceName.setWidth(Math.min(SETTINGS_CONTROL_WIDTH, NetherLinkFriendsScreen.this.width - 32));
            int fieldLeft = center - this.instanceName.getWidth() / 2;
            this.instanceNameLabel.setWidth(this.instanceName.getWidth());
            this.instanceNameLabel.alignLeft();
            this.instanceNameLabel.setPosition(fieldLeft, contentTop + 14);
            this.instanceName.setPosition(fieldLeft, contentTop + 26);
            this.apiButton.setWidth(Math.min(SETTINGS_CONTROL_WIDTH, NetherLinkFriendsScreen.this.width - 32));
            centerWidget(this.apiButton, center, contentTop + 58);
            this.apiSettingsLabel.setWidth(NetherLinkFriendsScreen.this.font.width(this.apiSettingsLabel.getMessage()));
            centerWidget(this.apiSettingsLabel, center, contentTop + 92);
            if (this.renderer != null) {
                int rendererTop = contentTop + 116;
                int rendererWidth = Math.min(SETTINGS_CONTROL_WIDTH, NetherLinkFriendsScreen.this.width - 32);
                this.renderer.doLayout(new ScreenRectangle(center - rendererWidth / 2, rendererTop, rendererWidth, SETTINGS_RENDERER_HEIGHT));
            }
            this.applyScrollVisibility();
            positionButtons(List.of(this.applyButton, this.doneButton), NetherLinkFriendsScreen.this.width, NetherLinkFriendsScreen.this.height - 28);
        }

        private boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (this.maxScroll() <= 0 || mouseY < this.scrollTop || mouseY >= this.scrollBottom) {
                return false;
            }
            int next = this.scrollOffset - (int)Math.signum(delta) * SETTINGS_SCROLL_STEP;
            this.scrollOffset = Mth.clamp(next, 0, this.maxScroll());
            NetherLinkFriendsScreen.this.repositionElements();
            return true;
        }

        private void renderScrollBar(GuiGraphics graphics) {
            int maxScroll = this.maxScroll();
            if (maxScroll <= 0 || this.scrollBottom <= this.scrollTop) {
                return;
            }
            int trackTop = this.scrollTop + 4;
            int trackBottom = this.scrollBottom - 4;
            int trackHeight = Math.max(1, trackBottom - trackTop);
            int viewportHeight = Math.max(1, this.scrollBottom - this.scrollTop);
            int thumbHeight = Mth.clamp(viewportHeight * trackHeight / Math.max(viewportHeight, this.contentHeight), 16, trackHeight);
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbTop = trackTop + (maxScroll == 0 ? 0 : this.scrollOffset * thumbTravel / maxScroll);
            int contentRight = NetherLinkFriendsScreen.this.width / 2 + Math.min(SETTINGS_CONTROL_WIDTH, NetherLinkFriendsScreen.this.width - 32) / 2;
            int x = Math.min(NetherLinkFriendsScreen.this.width - SETTINGS_SCROLLBAR_WIDTH - 4, contentRight + 8);
            graphics.fill(x, trackTop, x + SETTINGS_SCROLLBAR_WIDTH, trackBottom, 0x66000000);
            graphics.fill(x, thumbTop, x + SETTINGS_SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFFAAAAAA);
        }

        private int maxScroll() {
            return Math.max(0, this.contentHeight - Math.max(0, this.scrollBottom - this.scrollTop));
        }

        private void applyScrollVisibility() {
            this.applyScrollVisibility(this.instanceNameLabel);
            this.applyScrollVisibility(this.instanceName);
            this.applyScrollVisibility(this.apiButton);
            this.applyScrollVisibility(this.apiSettingsLabel);
            if (this.renderer != null) {
                this.renderer.visitChildren(this::applyScrollVisibility);
            }
        }

        private void applyScrollVisibility(AbstractWidget widget) {
            boolean visible = widget.getY() >= this.scrollTop && widget.getY() + widget.getHeight() <= this.scrollBottom;
            widget.visible = visible;
            if (!visible && widget.isFocused()) {
                widget.setFocused(false);
            }
        }

        private void updateButtons() {
            this.apiButton.setMessage(Component.translatable(
                "netherlink.friends.settings.api",
                ClientLinkSettings.serviceName(this.selectedServiceId)
            ));
            this.apiButton.active = !this.applying && ClientLinkSettings.availableServiceIds().size() > 1;
            this.instanceName.active = !this.applying;
            this.applyButton.active = !this.applying
                && (this.serviceChanged() || this.instanceNameChanged() || (this.renderer != null && this.renderer.canApply()));
        }

        private void cycleApi() {
            List<ResourceLocation> ids = ClientLinkSettings.availableServiceIds();
            if (ids.isEmpty()) {
                return;
            }
            int next = (ids.indexOf(this.selectedServiceId) + 1) % ids.size();
            this.selectedServiceId = ids.get(Math.max(0, next));
            this.rebuildRenderer();
            NetherLinkFriendsScreen.this.repositionElements();
            this.updateButtons();
        }

        private void rebuildRenderer() {
            LinkSettingsRenderer next;
            if (this.serviceChanged()) {
                next = new PendingLinkSettingsRenderer(NetherLinkFriendsScreen.this.minecraft, this.selectedServiceId);
                this.replaceRenderer(next);
                return;
            }
            LinkService current = LinkServices.current();
            next = LinkSettingsRenderers.create(new LinkSettingsContext(
                NetherLinkFriendsScreen.this.minecraft,
                current,
                () -> NetherLinkFriendsScreen.this.service,
                status -> NetherLinkFriendsScreen.this.status = status,
                NetherLinkFriendsScreen.this::refresh,
                this::updateButtons
            ));
            this.replaceRenderer(next);
            next.load();
        }

        private void replaceRenderer(LinkSettingsRenderer next) {
            if (this.renderer != null && this.active()) {
                this.renderer.visitChildren(NetherLinkFriendsScreen.this::removeWidget);
            }
            this.renderer = next;
            if (this.active()) {
                this.renderer.visitChildren(NetherLinkFriendsScreen.this::addRenderableWidget);
            }
        }

        private boolean active() {
            return NetherLinkFriendsScreen.this.tabManager != null
                && NetherLinkFriendsScreen.this.tabManager.getCurrentTab() == this;
        }

        private boolean serviceChanged() {
            return !LinkServices.current().id().equals(this.selectedServiceId)
                || ClientLinkSettings.requiresReload(NetherLinkFriendsScreen.this.minecraft, LinkServices.current());
        }

        private boolean instanceNameChanged() {
            return !this.savedInstanceName.equals(this.normalizedInstanceName());
        }

        private String normalizedInstanceName() {
            return this.instanceName.getValue().trim();
        }

        private void apply() {
            if (this.applying) {
                return;
            }
            this.applying = true;
            this.updateButtons();
            this.saveInstanceNameIfNeeded();
            if (this.serviceChanged()) {
                LinkService target = ClientLinkSettings.create(NetherLinkFriendsScreen.this.minecraft, this.selectedServiceId);
                this.applying = false;
                this.updateButtons();
                ClientTermsController.runAfterAcceptance(NetherLinkFriendsScreen.this.minecraft, NetherLinkFriendsScreen.this, target, () -> this.applyServiceSwitch(target));
                return;
            }
            if (this.renderer == null) {
                this.finishApply(false);
                return;
            }
            this.renderer.apply().whenComplete((rendererRefresh, error) -> NetherLinkFriendsScreen.this.minecraft.execute(() -> {
                if (error != null) {
                    this.applyFailed(error);
                    return;
                }
                this.finishApply(Boolean.TRUE.equals(rendererRefresh));
            }));
        }

        private void saveInstanceNameIfNeeded() {
            String value = this.normalizedInstanceName();
            if (this.savedInstanceName.equals(value)) {
                return;
            }
            ClientLinkSettings.saveInstanceName(NetherLinkFriendsScreen.this.minecraft, value);
            this.savedInstanceName = value;
            NetherLinkFriendsScreen.this.status = Component.translatable("netherlink.friends.settings.instance_name.saved").withStyle(ChatFormatting.GREEN);
        }

        private void applyServiceSwitch(LinkService target) {
            this.applying = true;
            this.updateButtons();
            NetherLinkFriendsScreen.this.status = Component.translatable(
                "netherlink.friends.settings.api.switching",
                target.name()
            ).withStyle(ChatFormatting.GRAY);
            ClientLinkSettings.selectService(NetherLinkFriendsScreen.this.minecraft, target)
                .whenComplete((ignored, error) -> NetherLinkFriendsScreen.this.minecraft.execute(() -> {
                    if (error != null) {
                        this.applyFailed(error);
                        return;
                    }
                    NetherLinkFriendsScreen.this.service = new ClientFriendService(NetherLinkFriendsScreen.this.minecraft);
                    this.selectedServiceId = LinkServices.current().id();
                    this.rebuildRenderer();
                    NetherLinkFriendsScreen.this.repositionElements();
                    this.applying = false;
                    this.updateButtons();
                    NetherLinkFriendsScreen.this.status = Component.translatable(
                        "netherlink.friends.settings.api.switched",
                        LinkServices.current().name()
                    ).withStyle(ChatFormatting.GREEN);
                    NetherLinkFriendsScreen.this.refresh();
                }));
        }

        private void finishApply(boolean refresh) {
            this.applying = false;
            this.updateButtons();
            if (refresh) {
                NetherLinkFriendsScreen.this.refresh();
            }
        }

        private void applyFailed(Throwable error) {
            this.applying = false;
            this.updateButtons();
            NetherLinkFriendsScreen.this.status = Component.translatable(
                "netherlink.friends.settings.failed",
                failureText(LinkFailures.from(error))
            ).withStyle(ChatFormatting.RED);
        }
    }

    private static final class SelectionList extends ObjectSelectionList<Row> {
        private final Consumer<Row> selectionChanged;
        private final Consumer<Row> activated;

        private SelectionList(Minecraft minecraft, Consumer<Row> selectionChanged, Consumer<Row> activated) {
            super(minecraft, 0, 0, 0, 0, LIST_ROW_HEIGHT);
            this.selectionChanged = selectionChanged;
            this.activated = activated;
            this.setRenderTopAndBottom(false);
        }

        private void setRows(List<Row> rows) {
            rows.forEach(row -> row.attach(this));
            this.replaceEntries(List.copyOf(rows));
            this.selectionChanged.accept(null);
        }

        private int top() {
            return this.y0;
        }

        private void updateSizeAndPosition(int width, int height, int top) {
            this.updateSize(width, height, top, top + height);
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            this.selectionChanged.accept(row);
        }

        @Override
        public int getRowWidth() {
            return Mth.clamp(this.width - 64, 280, 420);
        }

        @Override
        public int getRowLeft() {
            return this.x0 + (this.width - this.getRowWidth()) / 2;
        }

        @Override
        protected int getScrollbarPosition() {
            return Math.min(this.x1 - 8, this.getRowRight() + 6);
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

        protected int textOffset() {
            return 0;
        }

        protected void renderDecoration(GuiGraphics graphics, int left, int top, int width, int height) {
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int textX = left + 4 + this.textOffset();
            this.renderDecoration(graphics, left, top, width, height);
            graphics.drawString(minecraft.font, this.title(), textX, top + 3, -1);
            graphics.drawString(minecraft.font, this.description(), textX, top + 17, 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.owner.setSelected(this);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.empty().append(this.title()).append(Component.literal(". ")).append(this.description());
        }

    }

    private static final class SelfPresenceRow extends Row {
        private final String name;
        private final List<ClientFriendService.Instance> instances;
        private final Supplier<ResourceLocation> skin;

        private SelfPresenceRow(UUID profileId, String name, List<ClientFriendService.Instance> instances) {
            this.name = name;
            this.instances = List.copyOf(instances);
            this.skin = skin(new GameProfile(profileId, name));
        }

        @Override
        protected int textOffset() {
            return 28;
        }

        @Override
        protected void renderDecoration(GuiGraphics graphics, int left, int top, int width, int height) {
            PlayerFaceRenderer.draw(
                graphics,
                this.skin.get(),
                left + 4,
                top + (height - 24) / 2,
                24
            );
        }

        @Override
        protected Component title() {
            return selfPresenceTitle(this.name);
        }

        @Override
        protected Component description() {
            boolean joinable = this.instances.stream().anyMatch(ClientFriendService.Instance::joinable);
            return Component.translatable("netherlink.friends.self.instances.count", this.instances.size())
                .withStyle(joinable ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        }
    }

    private static final class FriendRow extends Row {
        private final ClientFriendService.Friend friend;
        private final Supplier<ResourceLocation> skin;

        private FriendRow(ClientFriendService.Friend friend) {
            this.friend = friend;
            this.skin = skin(new GameProfile(friend.profileId(), friend.name()));
        }

        @Override
        protected int textOffset() {
            return 28;
        }

        @Override
        protected void renderDecoration(GuiGraphics graphics, int left, int top, int width, int height) {
            PlayerFaceRenderer.draw(
                graphics,
                this.skin.get(),
                left + 4,
                top + (height - 24) / 2,
                24
            );
        }

        @Override
        protected Component title() {
            return Component.literal(this.friend.name());
        }

        @Override
        protected Component description() {
            if (this.friend.instances().isEmpty()) {
                return Component.translatable("netherlink.friends.status.offline").withStyle(ChatFormatting.DARK_GRAY);
            }
            boolean joinable = this.friend.instances().stream().anyMatch(ClientFriendService.Instance::joinable);
            return Component.translatable("netherlink.friends.instances.count", this.friend.instances().size())
                .withStyle(joinable ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        }
    }

    private static final class SelfInstanceRow extends Row {
        private final ClientFriendService.Instance instance;

        private SelfInstanceRow(ClientFriendService.Instance instance) {
            this.instance = instance;
        }

        @Override
        protected Component title() {
            return this.instance.displayText().isBlank()
                ? Component.translatable("netherlink.friends.self.instance")
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
        private final Supplier<ResourceLocation> skin;

        private RequestRow(ClientFriendService.Request request) {
            this.request = request;
            this.skin = skin(new GameProfile(request.profileId(), request.name()));
        }

        @Override
        protected int textOffset() {
            return 28;
        }

        @Override
        protected void renderDecoration(GuiGraphics graphics, int left, int top, int width, int height) {
            PlayerFaceRenderer.draw(
                graphics,
                this.skin.get(),
                left + 4,
                top + (height - 24) / 2,
                24
            );
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

    private static Supplier<ResourceLocation> skin(GameProfile profile) {
        AtomicReference<ResourceLocation> skin = new AtomicReference<>(DefaultPlayerSkin.getDefaultSkin(profile.getId()));
        Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                skin.set(location);
            }
        }, false);
        return skin::get;
    }

    private static Component statusText(LinkPresenceStatus status) {
        return switch (status) {
            case ONLINE -> Component.translatable("netherlink.friends.status.online");
            case PLAYING_OFFLINE -> Component.translatable("netherlink.friends.status.playing_offline");
            case PLAYING_REALMS -> Component.translatable("netherlink.friends.status.playing_realms");
            case PLAYING_SERVER -> Component.translatable("netherlink.friends.status.playing_server");
            case HOSTING -> Component.translatable("netherlink.friends.status.playing_hosted_server");
            case OFFLINE -> Component.translatable("netherlink.friends.status.offline");
            case UNKNOWN -> Component.translatable("netherlink.friends.status.unknown");
        };
    }

    private static Component selfPresenceTitle(String name) {
        return Component.empty()
            .append(Component.literal(name))
            .append(Component.literal(" "))
            .append(Component.translatable("netherlink.friends.self.instances.marker").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static Component actionResultText(LinkFriendActionResult result) {
        return switch (result) {
            case SERVICE_NOT_AVAILABLE -> Component.translatable("netherlink.failure.service_unavailable");
            case TOO_MANY_REQUESTS -> Component.translatable("netherlink.failure.rate_limited");
            case FORBIDDEN -> Component.translatable("netherlink.failure.forbidden");
            case UNKNOWN_PROFILE -> Component.translatable("netherlink.failure.profile_not_found");
            case ERROR, SUCCESS -> Component.translatable("netherlink.failure.unknown", result.name());
        };
    }

    static Component failureText(LinkFailure failure) {
        String key = switch (failure.code()) {
            case UNAUTHORIZED -> "netherlink.failure.unauthorized";
            case SERVICE_UNAVAILABLE -> "netherlink.failure.service_unavailable";
            case RATE_LIMITED -> "netherlink.failure.rate_limited";
            case FORBIDDEN -> "netherlink.failure.forbidden";
            case PROFILE_NOT_FOUND -> "netherlink.failure.profile_not_found";
            case ALREADY_FRIENDS -> "netherlink.failure.already_friends";
            case REQUEST_NOT_FOUND -> "netherlink.failure.request_not_found";
            case TARGET_UNAVAILABLE -> "netherlink.failure.target_unavailable";
            case TARGET_NOT_JOINABLE -> "netherlink.failure.target_not_joinable";
            case NOT_FRIENDS -> "netherlink.failure.not_friends";
            case INVALID_SESSION -> "netherlink.failure.invalid_session";
            case CONNECTION_LIMIT -> "netherlink.failure.connection_limit";
            case NETWORK -> "netherlink.failure.network";
            case TIMEOUT -> "netherlink.failure.timeout";
            case CANCELLED -> "netherlink.failure.cancelled";
            case INTERNAL -> "netherlink.failure.internal";
            case UNKNOWN -> null;
        };
        return key != null
            ? Component.translatable(key)
            : Component.translatable("netherlink.failure.unknown", failure.message());
    }
}
