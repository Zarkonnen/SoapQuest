import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Random;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import java.util.HashMap;

/**
 * Minigame is a very simple computer game implemented in less than 1000 LOC that hopefully has
 * some interesting rule-based gameplay. The player controls an avatar on a tile-based map who
 * can walk around using the arrow keys. The avatar can carry a single item. Walking into an item
 * on the map interacts with it, transforming it and the item the avatar is carrying. By this
 * simple mechanism, the avatar can pick things up and combine them with others. The avatar can
 * also drop things, which is done by pressing space and then indicating the direction of the tile
 * to drop the item on.
 * Finally, the game state can be saved by pressing s.
 * The game is largely data-driven, so a lot of extra stuff can be implemented by changing the csv
 * files.
 * You're welcome to modify/improve the code, though I suggest you strive for a certain simplicity.
 * The code formatting standards used here are as follows: Line wrap at 100, K&R style bracketing
 * unless the if condition is too big for a single line, tabs for indentation, two tabs for a
 * continued line.
 * Oh, and it's exactly 1024 lines, for reasons of silliness.
 *
 * This code is hereby released under the BSD licence. Feel free to modify and redistribute this as
 * you see fit.
 *
 * Original code by David Stark. david.stark@zarkonnen.com
*/
public class Minigame extends Canvas implements KeyListener {
	// Display & Graphics
	/** The frame the game is displayed in. */
	protected JFrame gameFrame;
	/** Font used for things' names. */
	public static final Font NAME_FONT = new Font("Verdana", Font.PLAIN, 10);
	/** Font used for the status bar. */
	public static final Font STATUS_FONT = new Font("Verdana", Font.PLAIN, 13);
	/** The pixel size of tiles. */
	public static final int TILE_SIZE = 40;
	/** How many tiles are displayed horizontally. */
	public static final int SCREEN_X_TILES = 20;
	/** How many tiles are displayed vertically. */
	public static final int SCREEN_Y_TILES = 13;
	/** Vertical space allocated for status bar. */
	public static final int STATUS_BAR_HEIGHT = 30;
	/** Screen width in pixels. */
	public static final int SCREEN_WIDTH = SCREEN_X_TILES * TILE_SIZE;
	/** Screen height in pixels. */
	public static final int SCREEN_HEIGHT = SCREEN_Y_TILES * TILE_SIZE + STATUS_BAR_HEIGHT;
	
	// Status display
	/** The name of the action performed, if any. */
	protected String action;
	/** Whether a transform has happened. */
	protected boolean transformDone;
		
	// Item Types
	// Each type item is defined by an unique ID number.
	/** The maximum number of item types. */
	public static final int NUMBER_OF_TYPES = 1024;
	// These are 2D lookup arrays determining what results from using one item on another.
	/** What the player ends up holding after using the 1st-index item on the 2nd-index item. */
	protected int[][] useCarriedResult = new int[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/** What ends up on the ground after using the 1st-index item on the 2nd-index item. */
	protected int[][] useTargetResult = new int[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/** Message displayed when one item is used on another. */
	protected String[][] useText = new String[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/** Mapping of item names to their type numbers. */
	protected HashMap<String, Integer> nameToType = new HashMap<String, Integer>();
	/** Array of canonical item names indexed by their type. */
	protected String[] typeNames = new String[NUMBER_OF_TYPES];
	/** Array of verbose item names indexed by their type. */
	protected String[] verboseTypeNames = new String[NUMBER_OF_TYPES];
	/** The display colour of each type. */
	protected Color[] typeColors = new Color[NUMBER_OF_TYPES];
	
	// Item behaviour
	/** Whether the given type wanders around randomly. */
	protected boolean[] typeWanders = new boolean[NUMBER_OF_TYPES];
	// These are 2D lookup arrays determining how items can directly interact with one another.
	/** What the item initiating the interaction ends up as. */
	protected int[][] interactResultA = new int[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/** What the item receiving the interaction ends up as. */
	protected int[][] interactResultB = new int[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/**
	 * The frequency at which items of the given types interact. 0 = many times a turn, 1 = once 
	 * a turn, 2 = once every two turns, etc.
	*/
	protected int[][] interactionFrequency = new int[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	public static final int ALWAYS = 0;
	/** The item type an item of a given type turns into after the given number of turns. */
	protected int[] changeType = new int[NUMBER_OF_TYPES];
	/** How old an item has to be to become an item of another type, or 0 for no change. */
	protected int[] changeAge = new int[NUMBER_OF_TYPES];
	/** What item types, if any, an item of this type seeks out. */
	protected boolean[][] soughtTypes = new boolean[NUMBER_OF_TYPES][NUMBER_OF_TYPES];
	/** Whether an item of this type seeks at all. */
	protected boolean typeSeeks[] = new boolean[NUMBER_OF_TYPES];
	/** How far a seeking item should look. Bigger numbers mean more "sight" but slower code. */
	public static final int SEEK_RANGE = 5;
	/** How far an item of this type lights things up. */
	protected int[] typeLight = new int[NUMBER_OF_TYPES];
	/**
	 * Array of types' supertypes. If a type has a supertype and no transform/interaction for a
	 * given situation, the program looks up its supertype.
	*/
	protected int[] supertype = new int[NUMBER_OF_TYPES];
	
	// Item types. Keep in mind the distinction between NONE and NOTHING.
	/** Type value indicating no transform, interaction or supertype. */
	public static final int NONE = -1;
	/** Type value indicating an empty tile, or holding nothing. */
	public static final int NOTHING = 0;
	/**
	 * Type value indicating anything at all - the supertype of all things that aren't ground. 
	*/
	public static final int ANYTHING = 1;
	/** Type value indicating a piece of ground - supertype of all kinds of ground. */
	public static final int GROUND = 2;
	/** Type value for the player. */
	public static final int PERSON = 3;
	/** Type value for grass. */
	public static final int GRASS = 4;
	/** Type value for the player's corpse. */
	public static final int CORPSE = 5;
	/** The first unused ID number. */
	public static final int FIRST_SAFE_ID = 6;
	
	// Map data
	/** Number of tiles in the map. */
	public static final int MAP_SIZE = 100;
	/** Number of layers in the map. */
	public static final int MAP_LAYERS = 2;
	/** The map layer of the ground. */
	public static final int GROUND_LAYER = 0;
	/** The map layer of the things on the ground. */
	public static final int PLAYER_LAYER = 1;
	/** The type on each map tile, or NOTHING if it's empty. */
	protected int[][][] map = new int[MAP_LAYERS][MAP_SIZE][MAP_SIZE];
	/** The age of each map tile. */
	protected int[][][] age = new int[MAP_LAYERS][MAP_SIZE][MAP_SIZE];
	/** Whether the given tile has been visited by mapTick. */
	protected boolean[][][] ticked = new boolean[MAP_LAYERS][MAP_SIZE][MAP_SIZE];
	/** Whether a move is currently occurring. */
	protected boolean moving = false;
	// The player's coordinates.
	protected int playerX;
	protected int playerY;
	/** What the player is carrying. */
	protected int carriedItem = NOTHING;
	/** The age of the item carried. */
	protected int carriedAge = 0;
	/** Whether the player has indicated they want to drop the item on the ground. */
	protected boolean wantToDrop = false;
	/** Which layer the player wants to interact with. */
	protected int useTargetZ = PLAYER_LAYER;
	/** Random source. */
	public static final Random RANDOM = new Random();
	// Arrays of adjacent relative locations.
	public static final int[] ADJACENT_X = new int[] { 1, -1, 0, 0, 0, 0 };
	public static final int[] ADJACENT_Y = new int[] { 0, 0, 1, -1, 0, 0 };
	public static final int[] ADJACENT_Z = new int[] { 0, 0, 0, 0, 1, -1 };
	/** Current turn. */
	protected int turn = 0;
	/** Day mode. */
	protected int dayMode = 0;
	/** Day mode interval. */
	public static final int DAY_MODE_INTERVAL = 20;
	/** Day mode brightness dividers. */
	public static final double[] DAY_MODE_BRIGHTNESS_MULTIPLIER = { 1, 1, 1, 1, 1, 0.9, 0.8, 0.65,
			0.5, 0.35, 0.3, 0.25, 0.2, 0.1, 0.15, 0.25, 0.3, 0.6, 0.8, 0.85, 0.9, 0.95, 1, 1, 1, 1,
			1, 1, 1, 1, 1, 1, 1, 1, 1 };
	/** How much a given tile is lit. */
	protected double[][] lit = new double[MAP_SIZE][MAP_SIZE];
	
	/** Entry point method - creates game and puts it into a window. */
	public static void main(String[] args) {
		Minigame g = new Minigame();
		g.setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
		JFrame window = new JFrame();
		window.add(g);
		window.pack();
		window.setResizable(false);
		window.addKeyListener(g);
		g.addKeyListener(g);
		window.setVisible(true);
	}
	
	/** Initialises the game. */
	protected Minigame() {
		initBaseRules();
		loadRules();
		loadMap();
		doLightCalculations();
	}
	
	protected HashMap<String, Clip> sounds = new HashMap<String, Clip>();
	
	protected void playSound(String name) {
		try {
			if (!sounds.containsKey(name)) {
				sounds.put(name, getClip(name));
			}
			sounds.get(name).setFramePosition(0);
			sounds.get(name).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** Plays sound from sounds folder. */
	protected Clip getClip(String name) throws Exception {
		AudioInputStream stream = AudioSystem.getAudioInputStream(new File(new File(getGameFolder(), "sounds"), name + ".wav"));
		DataLine.Info info = new DataLine.Info(Clip.class, stream.getFormat());
		Clip clip = (Clip) AudioSystem.getLine(info);
		clip.open(stream);
		return clip;
	}
	
	protected void initBaseRules() {
		// Initialise some base rules.
		
		// By default, no transforms or interactions happen.
		for (int carriedI = 0; carriedI < NUMBER_OF_TYPES; carriedI++) {
			for (int targetI = 0; targetI < NUMBER_OF_TYPES; targetI++) {
				useCarriedResult[carriedI][targetI] = NONE;
				useTargetResult[carriedI][targetI] = NONE;
			}
		}
		
		for (int a = 0; a < NUMBER_OF_TYPES; a++) {
			for (int b = 0; b < NUMBER_OF_TYPES; b++) {
				interactResultA[a][b] = NONE;
				interactResultB[a][b] = NONE;
			}
		}
		
		// "Anything" is the supertype of anything but anything, ground and nothing.
		for (int i = 0; i < NUMBER_OF_TYPES; i++) {
			supertype[i] = ANYTHING;
		}
		supertype[NOTHING] = NONE;
		supertype[ANYTHING] = NONE;
		supertype[GROUND] = NONE;
		supertype[GRASS] = GROUND;
		typeNames[NOTHING] = "nothing";
		verboseTypeNames[NOTHING] = "nothing";
		typeNames[PERSON] = "person";
		verboseTypeNames[PERSON] = "person";
		typeNames[GRASS] = "grass";
		verboseTypeNames[GRASS] = "grass";
		typeNames[CORPSE] = "corpse";
		verboseTypeNames[CORPSE] = "corpse";
		typeNames[ANYTHING] = "anything";
		verboseTypeNames[ANYTHING] = "anything";
		typeNames[GROUND] = "ground";
		verboseTypeNames[GROUND] = "ground";
		nameToType.put(typeNames[NOTHING], NOTHING);
		nameToType.put(typeNames[PERSON], PERSON);
		nameToType.put(typeNames[GRASS], GRASS);
		nameToType.put(typeNames[CORPSE], CORPSE);
		nameToType.put(typeNames[ANYTHING], ANYTHING);
		nameToType.put(typeNames[GROUND], GROUND);
		typeColors[PERSON] = new Color(191, 150, 130);
		typeColors[GRASS] = new Color(31, 210, 31);
		typeColors[CORPSE] = new Color(140, 0, 0);
	}
	
	// IO
	/** @return The folder the game jar is in. */
	protected File getGameFolder() throws Exception {
		return new File(
				Minigame.class.getProtectionDomain().getCodeSource().getLocation().toURI()).
				getAbsoluteFile().getParentFile();
	}
	
	// Load the game rules from CSV (ish) files.
	protected void loadRules() {
		String s = null;
		File f = null;
		
		try {
			// Types
			// CSV of <name>, <r>, <g>, <b> [, <verbose name>, [, supertype]]
			f = new File(getGameFolder(), "items.csv");
			BufferedReader r = new BufferedReader(new FileReader(f));
			int typeIndex = FIRST_SAFE_ID;
			while ((s = r.readLine()) != null) {
				// Ignore #-comments.
				if (s.trim().startsWith("#") || s.trim().length() == 0) { continue; }
				String[] bits = s.split(",", 6);
				typeNames[typeIndex] = bits[0].trim();
				typeColors[typeIndex] = new Color(
						Integer.parseInt(bits[1].trim()),
						Integer.parseInt(bits[2].trim()),
						Integer.parseInt(bits[3].trim()));
				if (bits.length > 4) {
					verboseTypeNames[typeIndex] = bits[4].trim();
				} else {
					verboseTypeNames[typeIndex] = typeNames[typeIndex];
				}
				if (bits.length > 5) {
					supertype[typeIndex] = nameToType.get(bits[5].trim());
				}

				nameToType.put(typeNames[typeIndex], typeIndex);
				typeIndex++;
			}
			r.close();
			
			// Transformations
			// One of two formats:
			// use, <carried>, <target>, <newCarried>, <newTarget>
			// pickup, <item>
			f = new File(getGameFolder(), "transformations.csv");
			r = new BufferedReader(new FileReader(f));
			while ((s = r.readLine()) != null) {
				// Ignore #-comments.
				if (s.trim().startsWith("#") || s.trim().length() == 0) { continue; }
				if (s.trim().startsWith("pickup")) {
					String[] bits = s.split(",", 2);
					int type = nameToType.get(bits[1].trim());
					useCarriedResult[NOTHING][type] = type;
					useTargetResult[NOTHING][type] = NOTHING;
					useText[NOTHING][type] = "pick up a " + verboseTypeNames[type];
				}
				if (s.trim().startsWith("use")) {
					String[] bits = s.split(",", 6);
					int carried = nameToType.get(bits[1].trim());
					int target = nameToType.get(bits[2].trim());
					int newCarried = nameToType.get(bits[3].trim());
					int newTarget = nameToType.get(bits[4].trim());
					useCarriedResult[carried][target] = newCarried;
					useTargetResult[carried][target] = newTarget;
					useText[carried][target] = bits[5].trim();
				}
			}
			r.close();
			
			// Behaviours
			// The following formats:
			// wander, <type>
			// interact, <sourceType>, <targetType>, <newSourceType>, <newTargetType>, <frequency>
			// change, <sourceType>, <targetType>, <age>
			// seek, <seekingType>, <soughtType>
			// light, <type>, <range>
			f = new File(getGameFolder(), "behaviours.csv");
			r = new BufferedReader(new FileReader(f));
			while ((s = r.readLine()) != null) {
				// Ignore #-comments.
				if (s.trim().startsWith("#") || s.trim().length() == 0) { continue; }
				if (s.trim().startsWith("wander")) {
					String[] bits = s.split(",", 2);
					int type = nameToType.get(bits[1].trim());
					typeWanders[type] = true;
				}
				if (s.trim().startsWith("interact")) {
					String[] bits = s.split(",", 6);
					int source = nameToType.get(bits[1].trim());
					int target = nameToType.get(bits[2].trim());
					int newSource = nameToType.get(bits[3].trim());
					int newTarget = nameToType.get(bits[4].trim());
					interactResultA[source][target] = newSource;
					interactResultB[source][target] = newTarget;
					interactionFrequency[source][target] = Integer.parseInt(bits[5].trim());
				}
				if (s.trim().startsWith("change")) {
					String[] bits = s.split(",", 4);
					int source = nameToType.get(bits[1].trim());
					int target = nameToType.get(bits[2].trim());
					changeType[source] = target;
					changeAge[source] = Integer.parseInt(bits[3].trim());
				}
				if (s.trim().startsWith("seek")) {
					String[] bits = s.split(",", 3);
					int seeker = nameToType.get(bits[1].trim());
					int sought = nameToType.get(bits[2].trim());
					soughtTypes[seeker][sought] = true;
					typeSeeks[seeker] = true;
				}
				if (s.trim().startsWith("light")) {
					String[] bits = s.split(",", 3);
					int type = nameToType.get(bits[1].trim());
					typeLight[type] = Integer.parseInt(bits[2].trim());
				}
			}
			r.close();
		} catch (Exception e) {
			System.err.println("Could not parse line in file " + f + ".");
			System.err.println(s);
			e.printStackTrace();
		}
	}
	
	/** Load the game map from save if existing, otherwise from default map. */
	protected void loadMap() {
		File f = null;	
		String s = null;
		try {
			// Init the map to contain grass on the ground.
			for (int y = 0; y < MAP_SIZE; y++) {
				for (int x = 0; x < MAP_SIZE; x++) {
					map[GROUND_LAYER][y][x] = GRASS;
				}
			}
			
			// First, <turn>
			// Then, <playerX>, <playerY>, <carriedItem> [, <carriedItemAge>]
			// Then, lines of <x>, <y>, <z>, <name> [, age]
			f = new File(getGameFolder(), "save.csv");
			if (!f.exists()) {
				f = new File(getGameFolder(), "map.csv");
			}
			BufferedReader r = new BufferedReader(new FileReader(f));
			
			int lineNumber = 0;
			while ((s = r.readLine()) != null) {
				// Ignore #-comments.
				if (s.trim().startsWith("#") || s.trim().length() == 0) { continue; }
				lineNumber++;
				// First, read in turn number.
				if (lineNumber == 1) {
					turn = Integer.parseInt(s.trim());
					dayMode = (turn / DAY_MODE_INTERVAL) % DAY_MODE_BRIGHTNESS_MULTIPLIER.length;
					continue;
				}
				// Second, read in player state.
				if (lineNumber == 2) {
					String[] bits = s.split(",", 4);
					playerX = Integer.parseInt(bits[0].trim());
					playerY = Integer.parseInt(bits[1].trim());
					carriedItem = nameToType.get(bits[2].trim());
					if (bits.length > 3) {
						carriedAge = Integer.parseInt(bits[3].trim());
					}
					continue;
				}
				// Then, read in location of items on map on each subsequent iteration.
				String[] bits = s.split(",", 5);
				int x = Integer.parseInt(bits[0].trim());
				int y = Integer.parseInt(bits[1].trim());
				int z = Integer.parseInt(bits[2].trim());
				map[z][y][x] = nameToType.get(bits[3].trim());
				if (bits.length > 4) {
					age[z][y][x] = Integer.parseInt(bits[4].trim());
				}
			}
			r.close();
		} catch (Exception e) {
			System.err.println("Could not parse line " + s + " in file " + f + ".");
			System.err.println(s);
			e.printStackTrace();
		}
	}
	
	/** Saves the game map/state to disk. */
	public void saveMap() {
		try {
			PrintWriter w = new PrintWriter(new File(getGameFolder(), "save.csv"));
			
			// Write turn number.
			w.println(turn);
			
			// Write player location.
			w.println(playerX + ", " + playerY + ", " + typeNames[carriedItem] + ", " +
					carriedAge);
			
			// Write location of items.
			for (int z = 0; z < MAP_LAYERS; z++) {
				for (int y = 0; y < MAP_SIZE; y++) {
					for (int x = 0; x < MAP_SIZE; x++) {
						// Skip NOTHING on the player level and GRASS on the ground.
						if (!(z == PLAYER_LAYER && map[z][y][x] == NOTHING) &&
							!(z == GROUND_LAYER && map[z][y][x] == GRASS))
						{
							w.println(x + ", " + y + ", " + z + ", " + typeNames[map[z][y][x]] +
									", " + age[z][y][x]);
						}
					}
				}
			}
			
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Drawing routines
	public void paint(Graphics g1) {
		Graphics2D g = (Graphics2D) g1;
		
		// Draw contents of tiles.
		for (int z = 0; z < MAP_LAYERS; z++) {
			for (int y = 0; y < SCREEN_Y_TILES; y++) {
				for (int x = 0; x < SCREEN_X_TILES; x++) {
					int mapX = x + playerX - SCREEN_X_TILES / 2;
					int mapY = y + playerY - SCREEN_Y_TILES / 2;
					if (mapX > -1 && mapX < MAP_SIZE && mapY > -1 && mapY < MAP_SIZE) {
						drawTile(g, x, y, z, map[z][mapY][mapX], lit[mapY][mapX]);
					}
				}
			}
		}
		
		// Draw indicator for where the player is.
		drawPlayer(g);
		
		// Draw item names, only for the layer the player is on.
		for (int y = 0; y < SCREEN_Y_TILES; y++) {
			for (int x = 0; x < SCREEN_X_TILES; x++) {
				int mapX = x + playerX - SCREEN_X_TILES / 2;
				int mapY = y + playerY - SCREEN_Y_TILES / 2;
				if (mapX > -1 && mapX < MAP_SIZE && mapY > -1 && mapY < MAP_SIZE) {
					drawTileName(g, x, y, PLAYER_LAYER, map[PLAYER_LAYER][mapY][mapX]);
				}
			}
		}
		
		drawStatusBar(g);
	}
	
	protected void drawPlayer(Graphics2D g) {
		// Draws a white circle significantly smaller than the tile size.
		g.setColor(Color.WHITE);
		g.fillOval(
				(SCREEN_X_TILES / 2) * TILE_SIZE + TILE_SIZE / 4 + 4,
				(SCREEN_Y_TILES / 2) * TILE_SIZE + TILE_SIZE / 4 + 4,
				TILE_SIZE / 2 - 8,
				TILE_SIZE / 2 - 8);
	}
	
	protected void drawTile(Graphics2D g, int x, int y, int z, int type, double l) {
		if (type != NOTHING) {
			// If it's night, make it dark.
			Color c = typeColors[type];
			if (l == 1) {
				g.setColor(c);
			} else {
				g.setColor(new Color((int) (c.getRed() * l), (int) (c.getGreen() * l), (int) (c.getBlue() * l)));
			}
			
			if (z == GROUND_LAYER) {
				// Render the ground layer as tiles.
				g.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
			} else {
				// Render items not at ground layer as ovals.
				g.fillOval(
						x * TILE_SIZE + TILE_SIZE / 4,
						y * TILE_SIZE + TILE_SIZE / 4,
						TILE_SIZE / 2,
						TILE_SIZE / 2);
			}
		}
	}
	
	protected void drawTileName(Graphics2D g, int x, int y, int z, int type) {
		// If there is an item here, draw its name.
		if (type != NOTHING) {
			g.setColor(Color.BLACK);
			g.setFont(NAME_FONT);
			// Split up the name into words to better fit it into a little square.
			String[] bits = verboseTypeNames[type].split(" ");
			for (int i = 0; i < bits.length; i++) {
				g.drawString(bits[i], x * TILE_SIZE, y * TILE_SIZE + 3 + i * 11);
			}
		}
	}
	
	protected void drawStatusBar(Graphics2D g) {
		String infoString = "Carrying: " + verboseTypeNames[carriedItem];
		
		if (action == null) {
			infoString += " || Arrows to move/pick up/use, d to drop, period to interact with " +
					"the ground, s to save.";
		} else {
			infoString += " || " + action;
		}
		
		g.setColor(transformDone ? new Color(255, 255, 191) : Color.WHITE);
		
		// Exception: the player has died.
		if (map[PLAYER_LAYER][playerY][playerX] == CORPSE) {
			infoString = "You have been killed.";
			g.setColor(Color.RED);
		}
		
		g.fillRect(0, SCREEN_HEIGHT - STATUS_BAR_HEIGHT, SCREEN_WIDTH, STATUS_BAR_HEIGHT);
		g.setColor(Color.BLACK);
		g.setFont(STATUS_FONT);
		g.drawString(infoString, 10, SCREEN_HEIGHT - 10);
	}
	
	// Keyboard
	/** Listens to keyboard presses. */
	public void keyPressed(KeyEvent e) {
		// If we're busy with a move, ignore.
		synchronized (this) {
			if (moving) { return; }
			moving = true;
		}
		
		// If the player is dead, don't respond to keyboard.
		if (map[PLAYER_LAYER][playerY][playerX] == CORPSE) { return; }
		
		switch (e.getKeyCode()) {
			// Movement/executing drops.
			case KeyEvent.VK_UP: {
				if (wantToDrop) {
					drop(0, -1);
				} else {
					movePlayer(0, -1);
				}
				break;
			}
			case KeyEvent.VK_DOWN: {
				if (wantToDrop) {
					drop(0, 1);
				} else {
					movePlayer(0, 1);
				}
				break;			}
			case KeyEvent.VK_LEFT: {
				if (wantToDrop) {
					drop(-1, 0);
				} else {
					movePlayer(-1, 0);
				}
				break;
			}
			case KeyEvent.VK_RIGHT: {
				if (wantToDrop) {
					drop(1, 0);
				} else {
					movePlayer(1, 0);
				}
				break;
			}
			case KeyEvent.VK_PERIOD: {
				if (useTargetZ == PLAYER_LAYER) {
					useTargetZ = GROUND_LAYER;
					action = "Interacting with the ground.";
				} else {
					useTargetZ = PLAYER_LAYER;
					action = "Interacting normally.";
				}
				repaint();
				break;
			}
			// Tell the program you want to drop something.
			case KeyEvent.VK_D: {
				if (wantToDrop) {
					wantToDrop = false;
					action = "Drop aborted.";
				} else {
					wantToDrop = true;
					action = "Please indicate drop direction by pressing an arrow key.";
				}
				repaint();
				break;
			}
			// Save the game.
			case KeyEvent.VK_S: {
				saveMap();
				action = "Saved to save.csv. Delete save.csv and restart to reset game.";
				repaint();
				break;
			}
		}
		
		// Allow for next move.
		synchronized (this) {
			moving = false;
		}
	}
	
	// Needed to implement KeyListener, but not needed.
	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}
	
	// Interaction
	protected void movePlayer(int dx, int dy) {
		// Calculate target coordinates of move.
		int newX = playerX + dx;
		int newY = playerY + dy;
		// Ignore if they are outside the map boundaries.
		if (newX < 0 || newX >= MAP_SIZE || newY < 0 || newY >= MAP_SIZE) {
			return;
		}
		
		int typeAtNewLocation = map[useTargetZ][newY][newX];
		
		if (typeAtNewLocation == NOTHING) {
			// If the place we want to move to is empty, move.
			// Note that the code explicitly does not allow for the player to switch layers.
			map[PLAYER_LAYER][newY][newX] = map[PLAYER_LAYER][playerY][playerX];
			age[PLAYER_LAYER][newY][newX] = age[PLAYER_LAYER][playerY][playerX];
			map[PLAYER_LAYER][playerY][playerX] = NOTHING;
			age[PLAYER_LAYER][playerY][playerX] = 0;
			playerX = newX;
			playerY = newY;
			// Set action and transformDone to null/false since we've only walked.
			action = null;
			transformDone = false;
		} else {
			// Don't allow interacting with something on the ground if there is something above it.
			if (useTargetZ == GROUND_LAYER && map[PLAYER_LAYER][newY][newX] != NOTHING) {
				action = "There is something standing on the ground here. Move it and try again.";
				transformDone = false; 
			} else {
				// If there is something there, try to interact with it.
				// Figure out what the item we're carrying/item on the ground turns into when we use
				// the one on the other. This is a bit complicated because we need to explore the
				// supertypes of both source and target. The key to understanding the code below is
				// that there is an assignment happening in the while condition!
				int newCarriedType = NONE;
				int newTargetType = NONE;
				int sourceType = carriedItem;
				int targetType = typeAtNewLocation;
				int mySourceType = sourceType;
				String myUseText = null;
				do {
					int myTargetType = targetType;
					do {
						if (useCarriedResult[mySourceType][myTargetType] != NONE) {
							newCarriedType = useCarriedResult[mySourceType][myTargetType];
							newTargetType = useTargetResult[mySourceType][myTargetType];
							myUseText = useText[mySourceType][myTargetType];
							
							// Cache this discovery for faster access next time.
							useCarriedResult[sourceType][targetType] = newCarriedType;
							useTargetResult[sourceType][targetType] = newTargetType;
							useText[sourceType][targetType] = myUseText;
						}
					} while (newCarriedType == NONE &&
							(myTargetType = supertype[myTargetType]) != NONE);
				} while (newCarriedType == NONE &&
						(mySourceType = supertype[mySourceType]) != NONE);
			
				// If there is a transform, do it.
				if (newCarriedType != NONE) {
					// Tell the user.
					transformDone = true;
					action = "You " + myUseText + ".";
					// Reset the transformed items' ages.
					if (newTargetType != typeAtNewLocation) {
						age[useTargetZ][newY][newX] = 0;
					}
					if (newCarriedType != carriedItem) {
						carriedAge = 0;
					}
					// Do the transform.
					carriedItem = newCarriedType;
					map[useTargetZ][newY][newX] = newTargetType;
				} else {
					// We can't do anything.
					transformDone = false;
					if (carriedItem == NOTHING) {
						action = "The " + verboseTypeNames[typeAtNewLocation] + " can't be " +
							"picked up.";
					} else {
						action = "You can't use a " + verboseTypeNames[carriedItem] + " on a " +
								verboseTypeNames[typeAtNewLocation] + ".";
					} // End check if action is pick up or something else.
				} // End check for being able to use.
			} // End check for item standing on the ground.
		} // End check if there is an item at the new location.
		
		// Reset where the player interactions point at.
		useTargetZ = PLAYER_LAYER;
		
		// Do a map tick.
		mapTick();
		
		repaint();
	}
	
	/** Drop the item carried onto the ground in the relative location given. */
	protected void drop(int dx, int dy) {
		int dropX = playerX + dx;
		int dropY = playerY + dy;
		if (dropX < 0 || dropX >= MAP_SIZE || dropY < 0 || dropY >= MAP_SIZE ||
			map[PLAYER_LAYER][dropY][dropX] != 0)
		{
			action = "Cannot drop item there.";
		} else {
			action = "Dropped " + verboseTypeNames[carriedItem] + ".";
			map[PLAYER_LAYER][dropY][dropX] = carriedItem;
			age[PLAYER_LAYER][dropY][dropX] = carriedAge;
			carriedItem = NOTHING;
		}
		
		wantToDrop = false;
		useTargetZ = PLAYER_LAYER;
		mapTick();
		repaint();
	}
	
	// Map behaviour
	/** Run the autonomous behaviour of map items. */
	protected void mapTick() {
		// Keep track of day and night.
		turn++;
		if (turn % DAY_MODE_INTERVAL == 0) {
			dayMode = (dayMode + 1) % DAY_MODE_BRIGHTNESS_MULTIPLIER.length;
		}
		
		// The code maintains a set of booleans to see whether a given item has already had its
		// turn. This is necessary because otherwise, an item that moves to a higher-indexed
		// location would get picked up by the loop again.
		for (int z = 0; z < MAP_LAYERS; z++) {
			for (int y = 0; y < MAP_SIZE; y++) {
				for (int x = 0; x < MAP_SIZE; x++) {
					ticked[z][y][x] = false;
				}
			}
		}
		
		// Now act upon each item.
		for (int z = 0; z < MAP_LAYERS; z++) {
			for (int y = 0; y < MAP_SIZE; y++) {
				for (int x = 0; x < MAP_SIZE; x++) {
					if (!ticked[z][y][x]) {
						// Increase the age of the item.
						ticked[z][y][x] = true;
						age[z][y][x]++;
						
						// Have it interact with others.
						boolean interactionDone = false;
						// Loop over all possible adjacent tiles.
						for (int direction = 0; direction < ADJACENT_X.length; direction++) {
							int targetX = x + ADJACENT_X[direction];
							int targetY = y + ADJACENT_Y[direction];
							int targetZ = z + ADJACENT_Z[direction];
							// Check the target tile is within the map.
							if (targetX > -1 && targetX < MAP_SIZE &&
								targetY > -1 && targetY < MAP_SIZE &&
								targetZ > -1 && targetZ < MAP_LAYERS)
							{
								// Check if there is an interaction. This is a bit complicated due
								// to type inheritance, so we need to explore the supertypes of 
								// both source and target. The key to understanding the code below
								// is that there is an assignment happening in the while condition!
								int newSourceType = NONE;
								int newTargetType = NONE;
								int sourceType = map[z][y][x];
								int targetType = map[targetZ][targetY][targetX];
								int mySourceType = sourceType;
								int freq = -1;
								do {
									int myTargetType = targetType;
									do {
										if (interactResultA[mySourceType][myTargetType] != NONE) {
											newSourceType = interactResultA[mySourceType][myTargetType];
											newTargetType = interactResultB[mySourceType][myTargetType];
											freq = interactionFrequency[mySourceType][myTargetType];
											
											// Cache this discovery for faster access next time.
											interactResultA[sourceType][targetType] = newSourceType;
											interactResultB[sourceType][targetType] = newTargetType;
											interactionFrequency[sourceType][targetType] = freq;
										}
									} while (newSourceType == NONE &&
											(myTargetType = supertype[myTargetType]) != NONE);
								} while (newSourceType == NONE &&
										(mySourceType = supertype[mySourceType]) != NONE);
								
								if (newSourceType != NONE) {
									// Check the frequency value: it should either be ALWAYS or
									// of a frequency where the age of the current item is a
									// multiple of the frequency, and with the item having done
									// no other interaction yet.
									if (freq == ALWAYS ||
										(age[z][y][x] % freq == 0 && !interactionDone))
									{
										// All right! The two items interact.
										// Reset the ages if necessary.
										if (sourceType != newSourceType) {
											age[z][y][x] = 0;
										}
										if (targetType != newTargetType) {
											age[targetZ][targetY][targetX] = 0;
										}
										// Transform.
										map[z][y][x] = newSourceType;
										map[targetZ][targetY][targetX] = newTargetType;
										ticked[targetZ][targetY][targetX] = true;
										// Note that we've done a transform.
										interactionDone = true;
									} // End check for frequency.
								} // End check for transform.
							} // End check for target within map and not NOTHING.
						} // End loop about directions.
						
						// If it's old enough, it might change into something else.
						if (changeAge[map[z][y][x]] != 0 &&
							age[z][y][x] >= changeAge[map[z][y][x]])
						{
							map[z][y][x] = changeType[map[z][y][x]];
							age[z][y][x] = 0;
						}
						
						// Seeking and wandering are next, but only count for non-ground tiles.
						if (z != PLAYER_LAYER) {
							continue;
						}
						
						// The item may have seeking behaviour.
						boolean hasMoved = false;
						
						// Check if it seeks.
						int myType = map[z][y][x];
						boolean seeking;
						do {
							seeking = typeSeeks[myType];
						} while (!seeking && (myType = supertype[myType]) != NONE);
						
						if (seeking) {
							// Cache the fact this item seeks.
							typeSeeks[map[z][y][x]] = true;
						
							// Now scan the surrounding area and try to find something to seek.
							int startX = Math.max(0, x - SEEK_RANGE);
							int endX = Math.min(MAP_SIZE, x + SEEK_RANGE + 1);
							int startY = Math.max(0, y - SEEK_RANGE);
							int endY = Math.min(MAP_SIZE, y + SEEK_RANGE + 1);
							
							int newX = 0;
							int newY = 0;
							
							boolean seekThisOne = false;
							for (int yy = startY; yy < endY && !seekThisOne; yy++) {
								for (int xx = startX; xx < endX && !seekThisOne; xx++) {
									if (x == xx && y == yy) { continue; }
									// Now, we need to explore the supertype structures and see if
									// this item seeks the candidate item.
									int candidateType = map[PLAYER_LAYER][yy][xx];
									if (candidateType == NOTHING) { continue; } // Optimisation.
									myType = map[z][y][x];
									do {
										int myCandidateType = candidateType;
										do {
											if (soughtTypes[myType][myCandidateType]) {
												// Cache.
												soughtTypes[map[z][y][x]][map[PLAYER_LAYER][yy][xx]] = true;
												seekThisOne = true;
												newX = x == xx ? x : x + (xx - x) / Math.abs(xx - x);
												newY = y == yy ? y : y + (yy - y) / Math.abs(yy - y);
											}
										} while (!seekThisOne &&
												(myCandidateType = supertype[myCandidateType]) != NONE);
									} while (!seekThisOne &&
											(myType = supertype[myType]) != NONE);
								} // End loop over x-axis.
							} // End loop over y-axis.
							
							// Try to actually move that way.
							if (seekThisOne && map[z][newY][newX] == NOTHING) {
								map[z][newY][newX] = map[z][y][x];
								age[z][newY][newX] = age[z][y][x];
								ticked[z][newY][newX] = true;
								map[z][y][x] = NOTHING;
								hasMoved = true;
							}
						} // End seeking.
					
						// If the item wanders around, make it move.
						if (!hasMoved && typeWanders[map[z][y][x]]) {
							// Choose a random direction.
							int direction = RANDOM.nextInt(ADJACENT_X.length);
							int newX = x + ADJACENT_X[direction];
							int newY = y + ADJACENT_Y[direction];
							// Don't make it wander in the z-direction.
							// Check the target loc is free/we're actually moving.
							if (newX > -1 && newX < MAP_SIZE && newY > -1 && newY < MAP_SIZE &&
								map[z][newY][newX] == NOTHING)
							{
								// Move it and tick its new location.
								map[z][newY][newX] = map[z][y][x];
								age[z][newY][newX] = age[z][y][x];
								ticked[z][newY][newX] = true;
								map[z][y][x] = NOTHING;
							} // End check if wander location free.
						} // End check if item type wanders.
					} // End check if tile already ticked.
				} // x
			} // y
		} // z
		
		// And don't forget aging/changing the item the player may be holding.
		if (carriedItem != NOTHING) {
			carriedAge++;
			if (changeAge[carriedItem] != 0 && carriedAge >= changeAge[carriedItem])
			{
				carriedItem = changeType[carriedItem];
				carriedAge = 0;
			}
		}
		
		doLightCalculations();
	}
	
	protected void doLightCalculations() {
		// Light up the map.
		for (int y = 0; y < MAP_SIZE; y++) { for (int x = 0; x < MAP_SIZE; x++) {
			lit[y][x] = DAY_MODE_BRIGHTNESS_MULTIPLIER[dayMode];
		}}
		
		for (int z = 0; z < MAP_LAYERS; z++) { for (int y = 0; y < MAP_SIZE; y++) { for (int x = 0; x < MAP_SIZE; x++) {
			int light = typeLight[map[z][y][x]];
			double intensity = light / 6.0;
			if (light != 0) {
				light = Math.abs(light);
				int startX = Math.max(0, x - light);
				int endX = Math.min(MAP_SIZE, x + light + 1);
				int startY = Math.max(0, y - light);
				int endY = Math.min(MAP_SIZE, y + light + 1);
				for (int yy = startY; yy < endY; yy++) { for (int xx = startX; xx < endX; xx++) {
					lit[yy][xx] = Math.max(0, Math.min(1, lit[yy][xx] +
							intensity / ((x - xx) * (x - xx) + (y - yy) * (y - yy) + 1)));
				} } // End inner y/x loop
			} // End check for light
		} } } // End z/y/x loop
		
		if (carriedItem != NONE && typeLight[carriedItem] != 0) {
			int light = typeLight[carriedItem];
			double intensity = light / 6.0;
			light = Math.abs(light);
			int startX = Math.max(0, playerX - light);
			int endX = Math.min(MAP_SIZE, playerX + light + 1);
			int startY = Math.max(0, playerY - light);
			int endY = Math.min(MAP_SIZE, playerY + light + 1);
			for (int yy = startY; yy < endY; yy++) { for (int xx = startX; xx < endX; xx++) {
				lit[yy][xx] = Math.max(0, Math.min(1, lit[yy][xx] +
						intensity / ((playerX - xx) * (playerX - xx) + (playerY - yy) * (playerY - yy) + 1)));
			} } // End inner y/x loop
		}
	}
}