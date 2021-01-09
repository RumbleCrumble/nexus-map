package net.antipixel.nexus;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.antipixel.nexus.definition.IconDefinition;
import net.antipixel.nexus.definition.RegionDefinition;
import net.antipixel.nexus.definition.TeleportDefinition;
import net.antipixel.nexus.sprites.SpriteDefinition;
import net.antipixel.nexus.ui.UIButton;
import net.antipixel.nexus.ui.UIFadeButton;
import net.antipixel.nexus.ui.UIGraphic;
import net.antipixel.nexus.ui.UIPage;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SpriteID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = "Nexus Menu Map",
        description = "Replaces the player owned house teleport Nexus menu",
        tags = {"poh", "portal", "teleport", "nexus"}
)
public class NexusMapPlugin extends Plugin
{
	/* Packed Widget IDs */
	private static final int GROUP_NEXUS_PORTAL = 17;
	private static final int ID_PORTAL_PANEL = 0x110002;
	private static final int ID_PORTAL_MODEL = 0x110003;
	private static final int ID_SCRY_TEXT = 0x110004;
	private static final int ID_SCRY_SELECT = 0x110005;
	private static final int ID_KEYEVENTS_ALTERNATE = 0x110007;
	private static final int ID_KEYEVENTS_PRIMARY = 0x110008;
	private static final int ID_SCROLLBOX_BORDER = 0x110009;
	private static final int ID_SCRY_RADIO_PANE = 0x11000A;
	private static final int ID_TELEPORT_LIST = 0x11000B;
	private static final int ID_LOC_LABELS_PRIMARY = 0x11000C;
	private static final int ID_SCROLLBAR = 0x11000E;
	private static final int ID_LOC_LABELS_ALTERNATE = 0x110010;

	/* Widget dimensions and positions */
	private static final int TELE_ICON_SIZE = 24;
	private static final int MAP_SPRITE_POS_X = 32;
	private static final int MAP_SPRITE_POS_Y = 18;
	private static final int INDEX_MAP_SPRITE_WIDTH = 400;
	private static final int INDEX_MAP_SPRITE_HEIGHT = 214;
	private static final int REGION_MAP_SPRITE_WIDTH = 478;
	private static final int REGION_MAP_SPRITE_HEIGHT = 272;
	private static final int MAP_ICON_WIDTH = 50;
	private static final int MAP_ICON_HEIGHT = 41;

	/* Script, Sprite IDs */
	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final int REGION_MAP_MAIN = 2721;

	/* Menu actions */
	private static final String ACTION_TEXT_TELE = "Teleport";
	private static final String ACTION_TEXT_SELECT = "Select";
	private static final String ACTION_TEXT_BACK = "Back";

	/* Definition JSON files */
	private static final String DEF_FILE_REGIONS = "RegionDef.json";
	private static final String DEF_FILE_SPRITES = "SpriteDef.json";

	private static final String TELE_NAME_PATTERN = "<col=ffffff>(\\S)</col> :  (.+)";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private NexusConfig config;

	@Inject
	private SpriteManager spriteManager;

	private RegionDefinition[] regionDefinitions;
	private SpriteDefinition[] spriteDefinitions;

	private Map<String, Teleport> availableTeleports;

	/* Widgets */
	private List<Integer> hiddenWidgetIDs;
	private UIGraphic mapGraphic;
	private UIGraphic[] indexRegionGraphics;
	private UIButton[] indexRegionIcons;

	private UIPage indexPage;
	private List<UIPage> mapPages;

	@Override
	protected void startUp()
	{
		this.loadDefinitions();
		this.createHiddenWidgetList();

		// Add the custom sprites to the sprite manager
		this.spriteManager.addSpriteOverrides(spriteDefinitions);
	}

	@Provides
	NexusConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NexusConfig.class);
	}

	@Override
	protected void shutDown()
	{
		this.regionDefinitions = null;
		this.hiddenWidgetIDs.clear();

		// Remove the custom sprites
		this.spriteManager.removeSpriteOverrides(spriteDefinitions);
	}

	/**
	 * Loads the definition files
	 */
	private void loadDefinitions()
	{
		// Construct an instance of GSON
		Gson gson = new Gson();

		// Load the definitions files for the regions and sprite override
		this.regionDefinitions = loadDefinitionResource(RegionDefinition[].class, DEF_FILE_REGIONS, gson);
		this.spriteDefinitions = loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES, gson);
	}

	/**
	 * Loads a definition resource from a JSON file
	 * @param classType the class into which the data contained in the JSON file will be read into
	 * @param resource the name of the resource (file name)
	 * @param gson a reference to the GSON object
	 * @param <T> the class type
	 * @return the data read from the JSON definition file
	 */
	private <T> T loadDefinitionResource(Class<T> classType, String resource, Gson gson)
	{
		// Load the resource as a stream and wrap it in a reader
		InputStream resourceStream = classType.getResourceAsStream(resource);
		InputStreamReader definitionReader = new InputStreamReader(resourceStream);

		// Load the objects from the JSON file
		return gson.fromJson(definitionReader, classType);
	}

	/**
	 * Creates a list of widgets that the plugin does not require
	 * in order to function, for them to be hidden and shown as required
	 */
	private void createHiddenWidgetList()
	{
		this.hiddenWidgetIDs = new ArrayList<>();

		this.hiddenWidgetIDs.add(ID_PORTAL_MODEL);
		this.hiddenWidgetIDs.add(ID_SCRY_TEXT);
		this.hiddenWidgetIDs.add(ID_SCRY_SELECT);
		this.hiddenWidgetIDs.add(ID_SCROLLBOX_BORDER);
		this.hiddenWidgetIDs.add(ID_TELEPORT_LIST);
		this.hiddenWidgetIDs.add(ID_SCROLLBAR);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == GROUP_NEXUS_PORTAL)
		{
			Widget root = this.client.getWidget(ID_PORTAL_PANEL);

			// Hide the default widgets and take the root widget
			// layer and expand it so that it takes up the entirety
			// of the Nexus windows main content area
			this.setDefaultWidgetVisibility(false);
			this.expandRootWidget(root);

			// Builds a list of teleports that are
			// actually available to the player
			this.buildAvailableTeleportList();

			// Create the page objects, onto which the UI
			// components will be placed
			this.createMenuPages();

			// Create the custom widgets
			this.createIndexMenu(root);
			this.createMapGraphic(root);
			this.createBackButton(root);
			this.createTeleportWidgets(root);

			// Finally, display the index page
			this.displayIndexPage();
		}
	}

	/**
	 * Shows or hides the default menu widgets
	 * @param visible the desired visibility state of the widgets,
	 *                true to set them to visible, false for hidden
	 */
	private void setDefaultWidgetVisibility(boolean visible)
	{
		// Iterate though each of the non essential widgets
		for (Integer packedID : this.hiddenWidgetIDs)
		{
			// Update their visibility
			this.client.getWidget(packedID).setHidden(!visible);
		}
	}

	/**
	 * Expands the size of the root widget to fill the entirety
	 * of the Nexus portal menu's content area
	 * @param root the root widget
	 */
	private void expandRootWidget(Widget root)
	{
		root.setOriginalX(0);
		root.setOriginalY(35);
		root.setWidthMode(WidgetSizeMode.ABSOLUTE);
		root.setHeightMode(WidgetSizeMode.ABSOLUTE);
		root.setOriginalWidth(REGION_MAP_SPRITE_WIDTH);
		root.setOriginalHeight(REGION_MAP_SPRITE_HEIGHT);
	}

	/**
	 * Constructs the list of teleports available for the player to use
	 */
	private void buildAvailableTeleportList()
	{
		this.availableTeleports = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(TELE_NAME_PATTERN);

		// Get the parent widgets containing the label list, for both
		// the primary type teleports and alternate type
		Widget primaryParent = this.client.getWidget(ID_LOC_LABELS_PRIMARY);
		Widget alternateParent = this.client.getWidget(ID_LOC_LABELS_ALTERNATE);

		// Fetch all teleports for both the primary and alternate teleport widgets,
		// appending the results of both to the available teleports maps
		this.availableTeleports.putAll(this.getTeleportsFromLabelWidget(primaryParent, false, labelPattern));
		this.availableTeleports.putAll(this.getTeleportsFromLabelWidget(alternateParent, true, labelPattern));
	}

	/**
	 * Extracts information from a nexus portals teleport list and returns the information as a Teleport list,
	 * containing the name, index, shortcut key and type of teleport (either primary or alternate)
	 * @param labelParent the widget containing a teleport list
	 * @param alt true if this widget contains alternate teleports, false if primary
	 * @param pattern a compiled pattern for matching the text contained in the list item widgets
	 * @return a list containing all available teleports for the provided widget
	 */
	private Map<String, Teleport> getTeleportsFromLabelWidget(Widget labelParent, boolean alt, Pattern pattern)
	{
		// Grab the children of the widget, each of which have a text
		// attribute containing the teleport location name and key shortcut
		Widget[] labelWidgets = labelParent.getDynamicChildren();

		// Create a map in which to place the available teleport options
		Map<String, Teleport> teleports = new HashMap<>();

		for (Widget child : labelWidgets)
		{
			// Create a pattern matcher with the widgets text content
			Matcher matcher = pattern.matcher(child.getText());

			// If the text doesn't match the pattern, skip onto the next
			if (!matcher.matches())
				continue;

			// Extract the pertinent information
			String shortcutKey = matcher.group(1);
			String teleportName =  matcher.group(2);

			// Construct a new teleport object for us to add to the map of available teleports
			teleports.put(teleportName, new Teleport(teleportName, child.getIndex(), shortcutKey, alt));
		}

		return teleports;
	}

	/**
	 * Creates the pages for the nexus menu, which are used to group the
	 * various UI components in order to conveniently switch between them
	 */
	private void createMenuPages()
	{
		this.indexPage = new UIPage();
		this.mapPages = new ArrayList<>(regionDefinitions.length);

		// Add a page for each region
		for (int i = 0; i < regionDefinitions.length; i++)
			this.mapPages.add(new UIPage());
	}

	/**
	 * Creates the widgets and components required for the index menu,
	 * such as the index maps and the region icons
	 * @param root the layer on which to create the widgets
	 */
	private void createIndexMenu(Widget root)
	{
		// Create a graphic widget for the background image of the index page
		Widget backingWidget = root.createChild(-1, WidgetType.GRAPHIC);

		// Wrap in a UIGraphic, set dimensions, position and sprite
		UIGraphic indexBackingGraphic = new UIGraphic(backingWidget);
		indexBackingGraphic.setPosition(MAP_SPRITE_POS_X, MAP_SPRITE_POS_Y);
		indexBackingGraphic.setSize(INDEX_MAP_SPRITE_WIDTH, INDEX_MAP_SPRITE_HEIGHT);
		indexBackingGraphic.setSprite(REGION_MAP_MAIN);

		// Initialise the arrays for the map graphics and icons
		this.indexRegionGraphics = new UIGraphic[regionDefinitions.length];
		this.indexRegionIcons = new UIButton[regionDefinitions.length];

		// Add the backing graphic to the index page
		this.indexPage.add(indexBackingGraphic);

		for (int i = 0; i < regionDefinitions.length; i++)
		{
			// Get definition for the region
			RegionDefinition regionDef = this.regionDefinitions[i];

			// Create a widget for the region sprite graphic
			Widget regionGraphic = root.createChild(-1, WidgetType.GRAPHIC);

			// Wrap in UIGraphic, update the size and position to match that of
			// the backing graphic. Set the sprite to that of the current region
			this.indexRegionGraphics[i] = new UIGraphic(regionGraphic);
			this.indexRegionGraphics[i].setPosition(MAP_SPRITE_POS_X, MAP_SPRITE_POS_Y);
			this.indexRegionGraphics[i].setSize(INDEX_MAP_SPRITE_WIDTH, INDEX_MAP_SPRITE_HEIGHT);
			this.indexRegionGraphics[i].setSprite(regionDef.getIndexSprite());

			// Add the component to the index page
			this.indexPage.add(this.indexRegionGraphics[i]);

			// If there's no teleports defined for this region, skip onto the next
			// before the icon widget is created and has its listeners attached
			if (!regionDef.hasTeleports())
				continue;

			// Create the widget for the regions icon
			Widget regionIcon = root.createChild(-1, WidgetType.GRAPHIC);

			// Get the definition for the regions icon
			IconDefinition iconDef = regionDef.getIcon();

			// Wrap in UIBUtton, position the component. attach listeners, etc.
			this.indexRegionIcons[i] = new UIButton(regionIcon);
			this.indexRegionIcons[i].setName(regionDef.getName());
			this.indexRegionIcons[i].setPosition(iconDef.getX(), iconDef.getY());
			this.indexRegionIcons[i].setSize(MAP_ICON_WIDTH, MAP_ICON_HEIGHT);
			this.indexRegionIcons[i].setSprites(iconDef.getSpriteStandard(), iconDef.getSpriteHover());
			this.indexRegionIcons[i].setOnHoverListener((c) -> onIconHover(regionDef.getID()));
			this.indexRegionIcons[i].setOnLeaveListener((c) -> onIconLeave(regionDef.getID()));
			this.indexRegionIcons[i].addAction(ACTION_TEXT_SELECT, () -> onIconClicked(regionDef.getID()));

			// Add to the index page
			this.indexPage.add(this.indexRegionIcons[i]);
		}
	}

	/**
	 * Creates the graphic used to display the custom map sprite on each of the map pages
	 * @param root the layer on which to create the widget
	 */
	private void createMapGraphic(Widget root)
	{
		// Create the widget for the map graphic
		Widget mapWidget = root.createChild(-1, WidgetType.GRAPHIC);

		// Wrap the widget in a UIGraphic
		this.mapGraphic = new UIGraphic(mapWidget);
		this.mapGraphic.setPosition(0, 0);
		this.mapGraphic.setSize(REGION_MAP_SPRITE_WIDTH, REGION_MAP_SPRITE_HEIGHT);

		// Add the map graphic to each of the map pages
		this.mapPages.forEach(page -> page.add(this.mapGraphic));
	}

	/**
	 * Creates the back arrow, used to return to the index page
	 * @param root the layer on which to create the widget
	 */
	private void createBackButton(Widget root)
	{
		// Create the widget for the button
		Widget backArrowWidget = root.createChild(-1, WidgetType.GRAPHIC);

		// Wrap as a button, set the position, sprite, etc.
		UIButton backArrowButton = new UIFadeButton(backArrowWidget);
		backArrowButton.setSprites(SpriteID.GE_BACK_ARROW_BUTTON);
		backArrowButton.setPosition(6, 6);
		backArrowButton.setSize(30, 23);

		// Assign the callback for the button
		backArrowButton.addAction(ACTION_TEXT_BACK, this::onBackButtonPressed);

		// Add the back arrow to each map page
		this.mapPages.forEach(page -> page.add(backArrowButton));
	}

	/**
	 * Creates the teleport icon widgets and places them
	 * in their correct position on the nexus widget pane
	 * @param root the layer on which to create the widget
	 */
	private void createTeleportWidgets(Widget root)
	{
		// Iterate through each of the map regions
		for (int i = 0; i < regionDefinitions.length; i++)
		{
			// Current map region
			RegionDefinition regionDef = this.regionDefinitions[i];

			// Get the definitions for the teleports within this map region
			TeleportDefinition[] teleportDefs = regionDef.getTeleports();

			// Iterate through each of the *defined* teleports, not just
			// the teleports that are available to the player
			for (TeleportDefinition teleportDef : teleportDefs)
			{
				// Create the teleport icon widget
				Widget teleportWidget = root.createChild(-1, WidgetType.GRAPHIC);

				// Create a button wrapper for the teleport widget. Set the dimensions,
				// the position and the visibility to hidden
				UIButton teleportButton = new UIButton(teleportWidget);
				teleportButton.setSize(TELE_ICON_SIZE, TELE_ICON_SIZE);
				teleportButton.setX(teleportDef.getSpriteX());
				teleportButton.setY(teleportDef.getSpriteY());
				teleportButton.setVisibility(false);

				// Add the teleport button to this regions map page
				this.mapPages.get(i).add(teleportButton);

				// Check that the teleport is available to the player
				if (this.isTeleportAvailable(teleportDef))
				{
					// Grab the teleport from the list of available teleports
					Teleport teleport = this.getAvailableTeleport(teleportDef);

					// Set the sprite to the active icon for this spell
					teleportButton.setSprites(teleportDef.getEnabledSprite());

					// Get the teleport name, formatted with alias
					String teleportName = getFormattedLocationName(teleportDef);

					// If enabled in the config, prepend the shortcut key for this
					// teleport to the beginning of the teleport name
					if (this.config.displayShortcuts())
						teleportName = this.prependShortcutKey(teleportName, teleport.getKeyShortcut());

					// Assign the teleport name
					teleportButton.setName(teleportName);

					// Add the menu options and listener, activate listeners
					teleportButton.addAction(ACTION_TEXT_TELE, () -> triggerTeleport(teleport));
				}
				else
				{
					// If the spell isn't available to the player, display the
					// deactivated spell icon instead
					teleportButton.setSprites(teleportDef.getDisabledSprite());
				}
			}
		}
	}

	/**
	 * Displays the index page and makes sure
	 * that each of the map pages are hidden
	 */
	private void displayIndexPage()
	{
		this.indexPage.setVisibility(true);
		this.mapPages.forEach(page -> page.setVisibility(false));
	}

	/**
	 * Displays the map page for the given region ID
	 * @param regionID the region ID to display
	 */
	private void displayMapPage(int regionID)
	{
		// Hide the index page
		this.indexPage.setVisibility(false);

		// Make sure all other map pages a hidden
		this.mapPages.forEach(page -> page.setVisibility(false));
		this.mapPages.get(regionID).setVisibility(true);

		// Set the sprite to that of the specified region
		this.mapGraphic.setSprite(regionDefinitions[regionID].getMapSprite());
	}

	/**
	 * Called when the mouse enters the icon
	 * @param regionID the ID of the region represented by the icon
	 */
	private void onIconHover(int regionID)
	{
		// Move the map sprite for this region up by 2 pixels, and
		// set the opacity to 75% opaque
		this.indexRegionGraphics[regionID].setY(MAP_SPRITE_POS_Y - 2);
		this.indexRegionGraphics[regionID].setOpacity(.75f);
		this.indexRegionGraphics[regionID].getWidget().revalidate();
	}

	/**
	 * Called when the mouse exits the icon
	 * @param regionID the ID of the region represented by the icon
	 */
	private void onIconLeave(int regionID)
	{
		// Restore the original position and set back to fully opaque
		this.indexRegionGraphics[regionID].setY(MAP_SPRITE_POS_Y);
		this.indexRegionGraphics[regionID].setOpacity(1.0f);
		this.indexRegionGraphics[regionID].getWidget().revalidate();
	}

	/**
	 * Called when a region icon is selected
	 * @param regionID the ID of the region represented by the icon
	 */
	private void onIconClicked(int regionID)
	{
		// Display the map page for the region
		this.displayMapPage(regionID);

		// *Boop*
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	/**
	 * Called when the back arrow has been pressed
	 */
	private void onBackButtonPressed()
	{
		// Go back to the index page
		this.displayIndexPage();

		// *Boop*
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	/**
	 * Teleports the player to the specified teleport location
	 * @param teleport the teleport location
	 */
    private void triggerTeleport(Teleport teleport)
    {
		// Get the appropriate widget parent for the teleport, depending
		// on whether the teleport is of primary or alternate type
		int packedID = teleport.isAlt() ? ID_KEYEVENTS_ALTERNATE : ID_KEYEVENTS_PRIMARY;

		// Get the child index of the teleport
    	final int widgetIndex = teleport.getChildIndex();

    	// Call a CS2 script which will trigger the widget's keypress event.
		// Credit to Abex for discovering this clever trick.
		this.clientThread.invokeLater(() -> client.runScript(SCRIPT_TRIGGER_KEY, packedID, widgetIndex));
    }

	/**
	 * Checks if there's a teleport available for a given teleport definition
	 * @param teleportDefinition the teleport definition
	 * @return true, if the teleport is available to the player, otherwise false
	 */
	private boolean isTeleportAvailable(TeleportDefinition teleportDefinition)
	{
		return this.availableTeleports.containsKey(teleportDefinition.getName());
	}

	/**
	 * Gets the teleport corresponding to the specified teleport definition
	 * @param teleportDefinition the teleport definition
	 * @return the teleport option
	 */
	private Teleport getAvailableTeleport(TeleportDefinition teleportDefinition)
	{
		return this.availableTeleports.get(teleportDefinition.getName());
	}

	/**
	 * Prepends the shortcut key to the teleport name
	 * @param name the teleport name
	 * @param key the shortcut key
	 * @return the teleport name with the shortcut key prepended
	 */
	private String prependShortcutKey(String name, String key)
	{
		return String.format("[%s] %s", key, name);
	}

	/**
	 * Creates a formatted string which is to be used as the name
	 * for the teleport icons. The string contains the base name of
	 * the teleport, and the alias name of the teleport, if is applicable.
	 * @param teleportDefinition the teleport definition
	 * @return the formatted string
	 */
	private String getFormattedLocationName(TeleportDefinition teleportDefinition)
	{
		// Create the base name
		String name = teleportDefinition.getName();

		// If this location has an alias, append it to the
		// end of the name string, enclosed in parenthesis
		if (teleportDefinition.hasAlias())
			name += String.format(" (%s)", teleportDefinition.getAlias());

		return name;
	}
}