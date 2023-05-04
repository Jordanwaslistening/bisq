/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.openoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.list.FilterBox;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.presentation.PortfolioUtil;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.DontShowAgainLookup;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.Date;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import static bisq.core.offer.OfferUtil.getRandomOfferId;
import static bisq.desktop.util.FormBuilder.getRegularIconButton;
import static bisq.desktop.util.FormBuilder.getRegularIconForLabel;

@FxmlView
public class OpenOffersView extends ActivatableViewAndModel<VBox, OpenOffersViewModel> {


    private enum ColumnNames {
        OFFER_ID(Res.get("shared.offerId")),
        MAKER_FEE_TX_ID(Res.get("openOffer.header.makerFeeTxId")),
        DATE(Res.get("shared.dateTime")),
        MARKET(Res.get("shared.market")),
        PRICE(Res.get("shared.price")),
        DEVIATION(Res.get("shared.deviation")),
        TRIGGER_PRICE(Res.get("openOffer.header.triggerPrice")),
        AMOUNT(Res.get("shared.BTCMinMax")),
        VOLUME(Res.get("shared.amountMinMax")),
        PAYMENT_METHOD(Res.get("shared.paymentMethod")),
        DIRECTION(Res.get("shared.offerType")),
        STATUS(Res.get("shared.state"));

        private final String text;

        ColumnNames(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @FXML
    TableView<OpenOfferListItem> tableView;
    @FXML
    TableColumn<OpenOfferListItem, OpenOfferListItem> priceColumn, deviationColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, offerIdColumn, deactivateItemColumn, makerFeeTxIdColumn,
            removeItemColumn, editItemColumn, triggerPriceColumn, triggerIconColumn, paymentMethodColumn, duplicateItemColumn;
    @FXML
    FilterBox filterBox;
    @FXML
    Label numItems;
    @FXML
    Region footerSpacer;
    @FXML
    AutoTooltipButton exportButton;
    @FXML
    AutoTooltipSlideToggleButton selectToggleButton;

    private final PriceFeedService priceFeedService;
    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private final BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow;
    private final OpenOfferManager openOfferManager;
    private SortedList<OpenOfferListItem> sortedList;
    private PortfolioView.OpenOfferActionHandler openOfferActionHandler;
    private ChangeListener<Number> widthListener;
    private ListChangeListener<OpenOfferListItem> sortedListeChangedListener;

    @Inject
    public OpenOffersView(OpenOffersViewModel model,
                          OpenOfferManager openOfferManager,
                          PriceFeedService priceFeedService,
                          Navigation navigation,
                          OfferDetailsWindow offerDetailsWindow,
                          BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow) {
        super(model);
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.bsqSwapOfferDetailsWindow = bsqSwapOfferDetailsWindow;
        this.openOfferManager = openOfferManager;
    }

    @Override
    public void initialize() {
        widthListener = (observable, oldValue, newValue) -> onWidthChange((double) newValue);
        makerFeeTxIdColumn.setGraphic(new AutoTooltipLabel(ColumnNames.MAKER_FEE_TX_ID.toString()));
        paymentMethodColumn.setGraphic(new AutoTooltipLabel(ColumnNames.PAYMENT_METHOD.toString()));
        priceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.PRICE.toString()));
        deviationColumn.setGraphic(new AutoTooltipTableColumn<>(ColumnNames.DEVIATION.toString(),
                Res.get("portfolio.closedTrades.deviation.help")).getGraphic());
        triggerPriceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRIGGER_PRICE.toString()));
        amountColumn.setGraphic(new AutoTooltipLabel(ColumnNames.AMOUNT.toString()));
        volumeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.VOLUME.toString()));
        marketColumn.setGraphic(new AutoTooltipLabel(ColumnNames.MARKET.toString()));
        directionColumn.setGraphic(new AutoTooltipLabel(ColumnNames.DIRECTION.toString()));
        dateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.DATE.toString()));
        offerIdColumn.setGraphic(new AutoTooltipLabel(ColumnNames.OFFER_ID.toString()));
        deactivateItemColumn.setGraphic(new AutoTooltipLabel(ColumnNames.STATUS.toString()));
        editItemColumn.setText("");
        duplicateItemColumn.setText("");
        removeItemColumn.setText("");

        setOfferIdColumnCellFactory();
        setMakerFeeTxIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setMarketColumnCellFactory();
        setPriceColumnCellFactory();
        setDeviationColumnCellFactory();
        setAmountColumnCellFactory();
        setVolumeColumnCellFactory();
        setPaymentMethodColumnCellFactory();
        setDateColumnCellFactory();
        setDeactivateColumnCellFactory();
        setEditColumnCellFactory();
        setTriggerIconColumnCellFactory();
        setTriggerPriceColumnCellFactory();
        setDuplicateColumnCellFactory();
        setRemoveColumnCellFactory();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.openOffers"))));

        offerIdColumn.setComparator(Comparator.comparing(o -> o.getOffer().getId()));
        makerFeeTxIdColumn.setComparator(Comparator.comparing(OpenOfferListItem::getMakerFeeTxId));
        directionColumn.setComparator(Comparator.comparing(o -> o.getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(OpenOfferListItem::getMarketDescription));
        amountColumn.setComparator(Comparator.comparing(o -> o.getOffer().getAmount()));
        priceColumn.setComparator(Comparator.comparing(o -> o.getOffer().getPrice(), Comparator.nullsFirst(Comparator.naturalOrder())));
        deviationColumn.setComparator(Comparator.comparing(OpenOfferListItem::getPriceDeviationAsDouble, Comparator.nullsFirst(Comparator.naturalOrder())));
        triggerPriceColumn.setComparator(Comparator.comparing(o -> o.getOpenOffer().getTriggerPrice(),
                Comparator.nullsFirst(Comparator.naturalOrder())));
        volumeColumn.setComparator(Comparator.comparing(o -> o.getOffer().getVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
        dateColumn.setComparator(Comparator.comparing(o -> o.getOffer().getDate()));
        paymentMethodColumn.setComparator(Comparator.comparing(o -> Res.get(o.getOffer().getPaymentMethod().getId())));

        dateColumn.setSortType(TableColumn.SortType.ASCENDING);
        tableView.getSortOrder().add(dateColumn);

        tableView.setRowFactory(
                tableView -> {
                    final TableRow<OpenOfferListItem> row = new TableRow<>();
                    final ContextMenu rowMenu = new ContextMenu();

                    MenuItem duplicateOfferMenuItem = new MenuItem(Res.get("portfolio.tab.duplicateOffer"));
                    duplicateOfferMenuItem.setOnAction((event) -> onDuplicateOffer(row.getItem()));
                    rowMenu.getItems().add(duplicateOfferMenuItem);

                    MenuItem cloneOfferMenuItem = new MenuItem(Res.get("shared.cloneOffer"));
                    cloneOfferMenuItem.setOnAction((event) -> onCloneOffer(row.getItem()));
                    rowMenu.getItems().add(cloneOfferMenuItem);

                    row.contextMenuProperty().bind(
                            Bindings.when(Bindings.isNotNull(row.itemProperty()))
                                    .then(rowMenu)
                                    .otherwise((ContextMenu) null));
                    return row;
                });

        selectToggleButton.setPadding(new Insets(0, 0, -20, 0));
        selectToggleButton.setText(Res.get("shared.enabled"));
        selectToggleButton.setDisable(true);
        HBox.setMargin(selectToggleButton, new Insets(0, 90, 0, 0));

        numItems.setId("num-offers");
        numItems.setPadding(new Insets(-5, 0, 0, 10));
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox.setMargin(exportButton, new Insets(0, 10, 0, 0));
        exportButton.updateText(Res.get("shared.exportCSV"));

        sortedListeChangedListener = c -> {
            c.next();
            if (c.wasAdded() || c.wasRemoved()) {
                updateNumberOfOffers();
                updateMakerFeeTxIdColumn();
            }
        };
    }

    @Override
    protected void activate() {
        FilteredList<OpenOfferListItem> filteredList = new FilteredList<>(model.dataModel.getList());
        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        sortedList.addListener(sortedListeChangedListener);
        tableView.setItems(sortedList);

        filterBox.initializeWithCallback(filteredList, tableView, this::updateNumberOfOffers);
        filterBox.activate();
        updateMakerFeeTxIdColumn();
        updateSelectToggleButtonState();

        selectToggleButton.setOnAction(event -> {
            if (model.isBootstrappedOrShowPopup()) {
                if (selectToggleButton.isSelected()) {
                    sortedList.forEach(openOfferListItem -> onActivateOpenOffer(openOfferListItem.getOpenOffer()));
                } else {
                    sortedList.forEach(openOfferListItem -> onDeactivateOpenOffer(openOfferListItem.getOpenOffer()));
                }
            }
            tableView.refresh();
        });

        exportButton.setOnAction(event -> {
            CSVEntryConverter<OpenOfferListItem> headerConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                for (ColumnNames m : ColumnNames.values()) {
                    columns[m.ordinal()] = m.toString();
                }
                return columns;
            };
            CSVEntryConverter<OpenOfferListItem> contentConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                columns[ColumnNames.OFFER_ID.ordinal()] = item.getOffer().getShortId();
                columns[ColumnNames.MAKER_FEE_TX_ID.ordinal()] = item.getMakerFeeTxId();
                columns[ColumnNames.DATE.ordinal()] = item.getDateAsString();
                columns[ColumnNames.MARKET.ordinal()] = item.getMarketDescription();
                columns[ColumnNames.PRICE.ordinal()] = item.getPriceAsString();
                columns[ColumnNames.DEVIATION.ordinal()] = item.getPriceDeviationAsString();
                columns[ColumnNames.TRIGGER_PRICE.ordinal()] = item.getTriggerPriceAsString();
                columns[ColumnNames.AMOUNT.ordinal()] = item.getAmountAsString();
                columns[ColumnNames.VOLUME.ordinal()] = item.getVolumeAsString();
                columns[ColumnNames.PAYMENT_METHOD.ordinal()] = item.getPaymentMethodAsString();
                columns[ColumnNames.DIRECTION.ordinal()] = item.getDirectionLabel();
                columns[ColumnNames.STATUS.ordinal()] = String.valueOf(!item.getOpenOffer().isDeactivated());
                return columns;
            };

            GUIUtil.exportCSV("openOffers.csv",
                    headerConverter,
                    contentConverter,
                    new OpenOfferListItem(null, null, null, null, null),
                    sortedList,
                    (Stage) root.getScene().getWindow());
        });

        root.widthProperty().addListener(widthListener);
        onWidthChange(root.getWidth());
    }

    private void updateNumberOfOffers() {
        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
    }

    private void updateMakerFeeTxIdColumn() {
        makerFeeTxIdColumn.setVisible(model.dataModel.getList().stream()
                .collect(Collectors.groupingBy(OpenOfferListItem::getMakerFeeTxId, Collectors.counting()))
                .values().stream().anyMatch(i -> i > 1));
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        sortedList.removeListener(sortedListeChangedListener);
        exportButton.setOnAction(null);

        filterBox.deactivate();
        root.widthProperty().removeListener(widthListener);
    }

    private void updateSelectToggleButtonState() {
        if (sortedList.size() == 0) {
            selectToggleButton.setDisable(true);
            selectToggleButton.setSelected(false);
        } else {
            selectToggleButton.setDisable(false);
            long numDeactivated = sortedList.stream()
                    .filter(openOfferListItem -> openOfferListItem.getOpenOffer().isDeactivated())
                    .count();
            if (numDeactivated == sortedList.size()) {
                selectToggleButton.setSelected(false);
            } else if (numDeactivated == 0) {
                selectToggleButton.setSelected(true);
            }
        }
    }

    private void onWidthChange(double width) {
        triggerPriceColumn.setVisible(width > 1300);
    }

    private void onDeactivateOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrappedOrShowPopup()) {
            model.onDeactivateOpenOffer(openOffer,
                    () -> log.debug("Deactivate offer was successful"),
                    (message) -> {
                        log.error(message);
                        new Popup().warning(Res.get("offerbook.deactivateOffer.failed", message)).show();
                    });
            updateSelectToggleButtonState();
        }
    }

    private void onActivateOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrappedOrShowPopup() && !model.dataModel.wasTriggered(openOffer)) {
            model.onActivateOpenOffer(openOffer,
                    () -> log.debug("Activate offer was successful"),
                    (message) -> {
                        log.error(message);
                        new Popup().warning(Res.get("offerbook.activateOffer.failed", message)).show();
                    });
            updateSelectToggleButtonState();
        }
    }

    private void onRemoveOpenOffer(OpenOfferListItem item) {
        OpenOffer openOffer = item.getOpenOffer();
        if (model.isBootstrappedOrShowPopup()) {
            if (openOfferManager.hasOfferSharedMakerFee(openOffer)) {
                doRemoveOpenOffer(openOffer);
            } else {
                String key = (openOffer.getOffer().isBsqSwapOffer() ? "RemoveBsqSwapWarning" : "RemoveOfferWarning");
                if (DontShowAgainLookup.showAgain(key)) {
                    String message = openOffer.getOffer().isBsqSwapOffer() ?
                            Res.get("popup.warning.removeNoFeeOffer") :
                            Res.get("popup.warning.removeOffer", item.getMakerFeeAsString());
                    new Popup().warning(message)
                            .actionButtonText(Res.get("shared.removeOffer"))
                            .onAction(() -> doRemoveOpenOffer(openOffer))
                            .closeButtonText(Res.get("shared.dontRemoveOffer"))
                            .dontShowAgainId(key)
                            .show();
                } else {
                    doRemoveOpenOffer(openOffer);
                }
            }
            updateSelectToggleButtonState();
        }
    }

    private void doRemoveOpenOffer(OpenOffer openOffer) {
        model.onRemoveOpenOffer(openOffer,
                () -> {
                    log.debug("Remove offer was successful");

                    tableView.refresh();

                    // We do not show the popup if it's a BSQ offer or a cloned offer with shared maker fee
                    if (openOffer.getOffer().isBsqSwapOffer() ||
                            openOfferManager.hasOfferSharedMakerFee(openOffer)) {
                        return;
                    }

                    String key = "WithdrawFundsAfterRemoveOfferInfo";
                    if (DontShowAgainLookup.showAgain(key)) {
                        new Popup().instruction(Res.get("offerbook.withdrawFundsHint", Res.get("navigation.funds.availableForWithdrawal")))
                                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key)
                                .show();
                    }
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning(Res.get("offerbook.removeOffer.failed", message)).show();
                });
    }

    private void onEditOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrappedOrShowPopup()) {
            openOfferActionHandler.onEditOpenOffer(openOffer);
        }
    }

    private void onDuplicateOffer(OpenOfferListItem item) {
        if (item == null || item.getOffer().getOfferPayloadBase() == null) {
            return;
        }
        PortfolioUtil.duplicateOffer(navigation, item.getOffer().getOfferPayloadBase());
    }

    private void onCloneOffer(OpenOfferListItem item) {
        if (item == null) {
            return;
        }
        OpenOffer openOffer = item.getOpenOffer();
        if (openOffer == null || openOffer.getOffer() == null || openOffer.getOffer().getOfferPayload().isEmpty()) {
            return;
        }

        Offer offer = openOffer.getOffer();
        OfferPayload sourceOfferPayload = offer.getOfferPayload().get();
        log.info("Clone offerPayload with shared maker fee: {}", sourceOfferPayload.getId());
        String newOfferId = getRandomOfferId();
        OfferPayload clonedOfferPayload = new OfferPayload(newOfferId,
                new Date().getTime(),
                sourceOfferPayload.getOwnerNodeAddress(),
                sourceOfferPayload.getPubKeyRing(),
                sourceOfferPayload.getDirection(),
                sourceOfferPayload.getPrice(),
                sourceOfferPayload.getMarketPriceMargin(),
                sourceOfferPayload.isUseMarketBasedPrice(),
                sourceOfferPayload.getAmount(),
                sourceOfferPayload.getMinAmount(),
                sourceOfferPayload.getBaseCurrencyCode(),
                sourceOfferPayload.getCounterCurrencyCode(),
                sourceOfferPayload.getArbitratorNodeAddresses(),
                sourceOfferPayload.getMediatorNodeAddresses(),
                sourceOfferPayload.getPaymentMethodId(),
                sourceOfferPayload.getMakerPaymentAccountId(),
                sourceOfferPayload.getOfferFeePaymentTxId(),
                sourceOfferPayload.getCountryCode(),
                sourceOfferPayload.getAcceptedCountryCodes(),
                sourceOfferPayload.getBankId(),
                sourceOfferPayload.getAcceptedBankIds(),
                sourceOfferPayload.getVersionNr(),
                sourceOfferPayload.getBlockHeightAtOfferCreation(),
                sourceOfferPayload.getTxFee(),
                sourceOfferPayload.getMakerFee(),
                sourceOfferPayload.isCurrencyForMakerFeeBtc(),
                sourceOfferPayload.getBuyerSecurityDeposit(),
                sourceOfferPayload.getSellerSecurityDeposit(),
                sourceOfferPayload.getMaxTradeLimit(),
                sourceOfferPayload.getMaxTradePeriod(),
                sourceOfferPayload.isUseAutoClose(),
                sourceOfferPayload.isUseReOpenAfterAutoClose(),
                sourceOfferPayload.getLowerClosePrice(),
                sourceOfferPayload.getUpperClosePrice(),
                sourceOfferPayload.isPrivateOffer(),
                sourceOfferPayload.getHashOfChallenge(),
                sourceOfferPayload.getExtraDataMap(),
                sourceOfferPayload.getProtocolVersion());
        Offer clonedOffer = new Offer(clonedOfferPayload);
        clonedOffer.setPriceFeedService(priceFeedService);
        offer.setState(Offer.State.OFFER_FEE_PAID);
        openOfferManager.placeOffer(clonedOffer,
                clonedOffer.getBuyerSecurityDeposit().getValue(),
                false,
                true,
                openOffer.getTriggerPrice(),
                transaction -> {
                },
                errorMessage -> new Popup().warning(errorMessage).show());
    }

    private void setOfferIdColumnCellFactory() {
        offerIdColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        offerIdColumn.getStyleClass().addAll("number-column", "first-column");
        offerIdColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem,
                            OpenOfferListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    hyperlinkWithIcon = new HyperlinkWithIcon(item.getOffer().getShortId());
                                    if (item.isNotPublished()) {
                                        // getStyleClass().add("offer-disabled"); does not work with hyperlinkWithIcon;-(
                                        hyperlinkWithIcon.setStyle("-fx-text-fill: -bs-color-gray-3;");
                                        hyperlinkWithIcon.getIcon().setOpacity(0.2);
                                    }
                                    hyperlinkWithIcon.setOnAction(event -> {
                                        if (item.getOffer().isBsqSwapOffer()) {
                                            bsqSwapOfferDetailsWindow.show(item.getOffer());
                                        } else {
                                            offerDetailsWindow.show(item.getOffer());
                                        }
                                    });

                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setMakerFeeTxIdColumnCellFactory() {
        makerFeeTxIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        makerFeeTxIdColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon = null;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    hyperlinkWithIcon = new HyperlinkWithIcon(item.getMakerFeeTxId());
                                    if (item.isNotPublished()) {
                                        // getStyleClass().add("offer-disabled"); does not work with hyperlinkWithIcon ;-(
                                        hyperlinkWithIcon.setStyle("-fx-text-fill: -bs-color-gray-3;");
                                        hyperlinkWithIcon.getIcon().setOpacity(0.2);
                                    }
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBlockExplorer(item.getMakerFeeTxId()));
                                    if (openOfferManager.hasOfferSharedMakerFee(item.getOpenOffer())) {
                                        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("offerbook.clonedOffer.info")));
                                    } else {
                                        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("offerbook.nonClonedOffer.info")));
                                    }
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null) {
                                        hyperlinkWithIcon.setOnAction(null);
                                    }
                                }
                            }
                        };


                        /*return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");
                                if (item != null) {
                                    if (item.isNotPublished()) {
                                        getStyleClass().add("offer-disabled");
                                    }
                                    Label label = new AutoTooltipLabel(item.getMakerFeeTxId());
                                    log.error("{} {}",openOfferManager.hasOfferSharedMakerFee(item.getOpenOffer()),item.getOpenOffer().getId());
                                    if (openOfferManager.hasOfferSharedMakerFee(item.getOpenOffer())) {
                                        Text icon = getRegularIconForLabel(MaterialDesignIcon.CALENDAR_QUESTION, label);
                                        label.setContentDisplay(ContentDisplay.LEFT);
                                       Tooltip.install(icon, new Tooltip(Res.get("offerbook.sharedMakerFeeTxId")));
                                    } else {
                                        label.setGraphic(null);
                                    }
                                    setGraphic(label);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };*/
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");
                                if (item != null) {
                                    if (item.isNotPublished()) {
                                        getStyleClass().add("offer-disabled");
                                    }
                                    setGraphic(new AutoTooltipLabel(item.getDateAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getAmountAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getPriceAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDeviationColumnCellFactory() {
        deviationColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        deviationColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    AutoTooltipLabel autoTooltipLabel = new AutoTooltipLabel(item.getPriceDeviationAsString());
                                    autoTooltipLabel.setOpacity(item.getOffer().isUseMarketBasedPrice() ? 1 : 0.4);
                                    setGraphic(autoTooltipLabel);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setTriggerPriceColumnCellFactory() {
        triggerPriceColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        triggerPriceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");
                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getTriggerPriceAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getVolumeAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setPaymentMethodColumnCellFactory() {
        paymentMethodColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getPaymentMethodAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getDirectionLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                getStyleClass().removeAll("offer-disabled");

                                if (item != null) {
                                    if (item.isNotPublished()) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(item.getMarketDescription()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDeactivateColumnCellFactory() {
        deactivateItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        deactivateItemColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            final ImageView iconView = new ImageView();
                            AutoTooltipSlideToggleButton checkBox;

                            private void updateState(@NotNull OpenOffer openOffer) {
                                checkBox.setSelected(!openOffer.isDeactivated());
                            }

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    OpenOffer openOffer = item.getOpenOffer();
                                    if (checkBox == null) {
                                        checkBox = new AutoTooltipSlideToggleButton();
                                        checkBox.setPadding(new Insets(-7, 0, -7, 0));
                                        checkBox.setGraphic(iconView);
                                    }
                                    checkBox.setDisable(model.dataModel.wasTriggered(openOffer));
                                    checkBox.setOnAction(event -> {
                                        if (openOffer.isDeactivated()) {
                                            onActivateOpenOffer(openOffer);
                                        } else {
                                            onDeactivateOpenOffer(openOffer);
                                        }
                                        updateState(openOffer);
                                        tableView.refresh();
                                    });
                                    updateState(openOffer);
                                    setGraphic(checkBox);
                                } else {
                                    setGraphic(null);
                                    if (checkBox != null) {
                                        checkBox.setOnAction(null);
                                        checkBox = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setRemoveColumnCellFactory() {
        removeItemColumn.getStyleClass().addAll("last-column", "avatar-column");
        removeItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        removeItemColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = getRegularIconButton(MaterialDesignIcon.DELETE_FOREVER, "delete");
                                        button.setTooltip(new Tooltip(Res.get("shared.removeOffer")));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onRemoveOpenOffer(item));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setDuplicateColumnCellFactory() {
        duplicateItemColumn.getStyleClass().add("avatar-column");
        duplicateItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        duplicateItemColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = getRegularIconButton(MaterialDesignIcon.CONTENT_COPY);
                                        button.setTooltip(new Tooltip(Res.get("shared.duplicateOffer")));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onDuplicateOffer(item));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setTriggerIconColumnCellFactory() {
        triggerIconColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        triggerIconColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = getRegularIconButton(MaterialDesignIcon.SHIELD_HALF_FULL);
                                        boolean triggerPriceSet = item.getOpenOffer().getTriggerPrice() > 0;
                                        button.setVisible(triggerPriceSet);

                                        if (model.dataModel.wasTriggered(item.getOpenOffer())) {
                                            button.getGraphic().getStyleClass().add("warning");
                                            button.setTooltip(new Tooltip(Res.get("openOffer.triggered")));
                                        } else {
                                            button.getGraphic().getStyleClass().remove("warning");
                                            button.setTooltip(new Tooltip(Res.get("openOffer.triggerPrice", item.getTriggerPriceAsString())));
                                        }
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onEditOpenOffer(item.getOpenOffer()));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setEditColumnCellFactory() {
        editItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        editItemColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getOffer().isBsqSwapOffer()) {
                                        if (button != null) {
                                            button.setOnAction(null);
                                            button = null;
                                        }
                                        if (item.getOpenOffer().isBsqSwapOfferHasMissingFunds()) {
                                            Label label = new Label();
                                            Text icon = getRegularIconForLabel(MaterialDesignIcon.EYE_OFF, label, "opaque-icon");
                                            Tooltip.install(icon, new Tooltip(Res.get("openOffer.bsqSwap.missingFunds")));
                                            setGraphic(icon);
                                        } else {
                                            setGraphic(null);
                                        }
                                    } else {
                                        if (button == null) {
                                            button = getRegularIconButton(MaterialDesignIcon.PENCIL);
                                            button.setTooltip(new Tooltip(Res.get("shared.editOffer")));
                                            button.setOnAction(event -> onEditOpenOffer(item.getOpenOffer()));
                                            setGraphic(button);
                                        }
                                    }
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    public void setOpenOfferActionHandler(PortfolioView.OpenOfferActionHandler openOfferActionHandler) {
        this.openOfferActionHandler = openOfferActionHandler;
    }
}

