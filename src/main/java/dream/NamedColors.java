package dream;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The colors named in Wikipedia's
 * <a href="https://en.wikipedia.org/wiki/List_of_colors:_A%E2%80%93F">List of
 * colors: A-F</a>, keyed by name so dialogue can ask for a color in words and
 * still draw with its real RGB value.
 *
 * <p>Names are matched ignoring case and extra whitespace, since that is what a
 * player actually types. The A-F table comes from the page above; common colors
 * outside it (red, green, purple, ...) use the standard web values.
 */
public final class NamedColors {

    private static final Map<String, Color> BY_KEY = new HashMap<>();
    private static final Map<String, String> NAME_BY_KEY = new HashMap<>();

    static {
        String[] entries = { // entries from Wikipedia's alphabetized list of colors
            "Absolute Zero|0048BA",
            "Acid green|B0BF1A",
            "Aero|7CB9E8",
            "African violet|B284BE",
            "Air superiority blue|72A0C1",
            "Alice blue|F0F8FF",
            "Alizarin|DB2D43",
            "Alloy orange|C46210",
            "Almond|EED9C4",
            "Amaranth deep purple|9F2B68",
            "Amaranth pink|F19CBB",
            "Amaranth purple|AB274F",
            "Amazon|3B7A57",
            "Amber|FFBF00",
            "Amethyst|9966CC",
            "Android green|3DDC84",
            "Antique brass|C88A65",
            "Antique bronze|665D1E",
            "Antique fuchsia|915C83",
            "Antique ruby|841B2D",
            "Antique white|FAEBD7",
            "Apricot|FBCEB1",
            "Aqua|00FFFF",
            "Aquamarine|7FFFD4",
            "Arctic lime|D0FF14",
            "Artichoke green|4B6F44",
            "Arylide yellow|E9D66B",
            "Ash gray|B2BEB5",
            "Atomic tangerine|FF9966",
            "Aureolin|FDEE00",
            "Azure|007FFF",
            "Azure (X11/web color)|F0FFFF",
            "Baby blue|89CFF0",
            "Baby blue eyes|A1CAF1",
            "Baby pink|F4C2C2",
            "Baby powder|FEFEFA",
            "Baker-Miller pink|FF91AF",
            "Banana Mania|FAE7B5",
            "Barbie Pink|DA1884",
            "Barn red|7C0A02",
            "Battleship grey|848482",
            "Beau blue|BCD4E6",
            "Beaver|9F8170",
            "Beige|F5F5DC",
            "B'dazzled blue|2E5894",
            "Big dip o'ruby|9C2542",
            "Bisque|FFE4C4",
            "Bistre|3D2B1F",
            "Bistre brown|967117",
            "Bitter lemon|CAE00D",
            "Bittersweet|FE6F5E",
            "Black|000000",
            "Black bean|3D0C02",
            "Black coral|54626F",
            "Black olive|3B3C36",
            "Black Shadows|BFAFB2",
            "Blanched almond|FFEBCD",
            "Blast-off bronze|A57164",
            "Bleu de France|318CE7",
            "Blizzard blue|ACE5EE",
            "Blood red|660000",
            "Blue|0000FF",
            "Blue (Crayola)|1F75FE",
            "Blue (Munsell)|0093AF",
            "Blue (NCS)|0087BD",
            "Blue (Pantone)|0018A8",
            "Blue (pigment)|333399",
            "Blue bell|A2A2D0",
            "Blue-gray (Crayola)|6699CC",
            "Blue jeans|5DADEC",
            "Blue sapphire|126180",
            "Blue-violet|8A2BE2",
            "Blue yonder|5072A7",
            "Bluetiful|3C69E7",
            "Blush|DE5D83",
            "Bole|79443B",
            "Bone|E3DAC9",
            "Brick red|CB4154",
            "Bright lilac|D891EF",
            "Bright yellow (Crayola)|FFAA1D",
            "British racing green|004225",
            "Bronze|CD7F32",
            "Brown|964B00",
            "Brown sugar|AF6E4D",
            "Bud green|7BB661",
            "Buff|FFC680",
            "Burgundy|800020",
            "Burlywood|DEB887",
            "Burnished brown|A17A74",
            "Burnt orange|CC5500",
            "Burnt sienna|E97451",
            "Burnt umber|8A3324",
            "Byzantine|BD33A4",
            "Byzantium|702963",
            "Cadet blue|5F9EA0",
            "Cadet grey|91A3B0",
            "Cadmium green|006B3C",
            "Cadmium orange|ED872D",
            "Café au lait|A67B5B",
            "Café noir|4B3621",
            "Cambridge blue|A3C1AD",
            "Camel|C19A6B",
            "Cameo pink|EFBBCC",
            "Canary|FFFF99",
            "Canary yellow|FFEF00",
            "Candy pink|E4717A",
            "Cardinal|C41E3A",
            "Caribbean green|00CC99",
            "Carmine|960018",
            "Carmine (M&P)|D70040",
            "Carnation pink|FFA6C9",
            "Carnelian|B31B1B",
            "Carolina blue|56A0D3",
            "Carrot orange|ED9121",
            "Catawba|703642",
            "Cedar Chest|C95A49",
            "Celadon|ACE1AF",
            "Celeste|B2FFFF",
            "Cerise|DE3163",
            "Cerulean|007BA7",
            "Cerulean blue|2A52BE",
            "Cerulean frost|6D9BC3",
            "Cerulean (Crayola)|1DACD6",
            "Cerulean (RGB)|0040FF",
            "Champagne|F7E7CE",
            "Champagne pink|F1DDCF",
            "Charcoal|36454F",
            "Charm pink|E68FAC",
            "Chartreuse (web)|80FF00",
            "Cherry blossom pink|FFB7C5",
            "Chestnut|954535",
            "Chili red|E23D28",
            "China pink|DE6FA1",
            "Chinese red|AA381E",
            "Chinese violet|856088",
            "Chinese yellow|FFB200",
            "Chocolate (traditional)|7B3F00",
            "Chocolate (web)|D2691E",
            "Cinereous|98817B",
            "Cinnabar|E34234",
            "Cinnamon Satin|CD607E",
            "Citrine|E4D00A",
            "Citron|9FA91F",
            "Claret|7F1734",
            "Coffee|6F4E37",
            "Columbia Blue|B9D9EB",
            "Congo pink|F88379",
            "Connor's Lakefront|175A6C",
            "Cool grey|8C92AC",
            "Copper|B87333",
            "Copper (Crayola)|DA8A67",
            "Copper penny|AD6F69",
            "Copper red|CB6D51",
            "Copper rose|996666",
            "Coquelicot|FF3800",
            "Coral|FF7F50",
            "Coral pink|F88379",
            "Cordovan|893F45",
            "Corn|FBEC5D",
            "Cornflower blue|6495ED",
            "Cornsilk|FFF8DC",
            "Cosmic cobalt|2E2D88",
            "Cosmic latte|FFF8E7",
            "Coyote brown|81613C",
            "Cotton candy|FFBCD9",
            "Cream|FFFDD0",
            "Crimson|DC143C",
            "Crimson (UA)|9E1B32",
            "Cultured Pearl|F5F5F5",
            "Cyan|00FFFF",
            "Cyan (process)|00B7EB",
            "Cyber grape|58427C",
            "Cyber yellow|FFD300",
            "Cyclamen|F56FA1",
            "Dandelion|FED85D",
            "Dark brown|654321",
            "Dark byzantium|5D3954",
            "Dark cyan|008B8B",
            "Dark electric blue|536878",
            "Dark goldenrod|B8860B",
            "Dark green (X11)|006400",
            "Dark jungle green|1A2421",
            "Dark khaki|BDB76B",
            "Dark lava|483C32",
            "Dark liver (horses)|543D37",
            "Dark magenta|8B008B",
            "Dark olive green|556B2F",
            "Dark orange|FF8C00",
            "Dark orchid|9932CC",
            "Dark purple|301934",
            "Dark red|8B0000",
            "Dark salmon|E9967A",
            "Dark sea green|8FBC8F",
            "Dark sienna|3C1414",
            "Dark sky blue|8CBED6",
            "Dark slate blue|483D8B",
            "Dark slate gray|2F4F4F",
            "Dark spring green|177245",
            "Dark turquoise|00CED1",
            "Dark violet|9400D3",
            "Davy's grey|555555",
            "Deep cerise|DA3287",
            "Deep champagne|FAD6A5",
            "Deep chestnut|B94E48",
            "Deep jungle green|004B49",
            "Deep pink|FF1493",
            "Deep saffron|FF9933",
            "Deep sky blue|00BFFF",
            "Deep Space Sparkle|4A646C",
            "Deep taupe|7E5E60",
            "Denim|1560BD",
            "Denim blue|2243B6",
            "Desert|C19A6B",
            "Desert sand|EDC9AF",
            "Dim gray|696969",
            "Dodger blue|1E90FF",
            "Drab dark brown|4A412A",
            "Duke blue|00009C",
            "Dutch white|EFDFBB",
            "Ebony|555D50",
            "Ecru|C2B280",
            "Eerie black|1B1B1B",
            "Eggplant|614051",
            "Eggshell|F0EAD6",
            "Electric lime|CCFF00",
            "Electric purple|BF00FF",
            "Electric violet|8F00FF",
            "Emerald|50C878",
            "Eminence|6C3082",
            "English lavender|B48395",
            "English red|AB4B52",
            "English vermillion|CC474B",
            "English violet|563C5C",
            "Erin|00FF40",
            "Eton blue|96C8A2",
            "Fallow|C19A6B",
            "Falu red|801818",
            "Fandango|B53389",
            "Fandango pink|DE5285",
            "Fawn|E5AA70",
            "Fern green|4F7942",
            "Field drab|6C541E",
            "Fiery rose|FF5470",
            "Finn|683068",
            "Firebrick|B22222",
            "Fire engine red|CE2029",
            "Flame|E25822",
            "Flax|EEDC82",
            "Flirt|A2006D",
            "Floral white|FFFAF0",
            "Forest green (web)|228B22",
            "French beige|A67B5B",
            "French bistre|856D4D",
            "French blue|0072BB",
            "French fuchsia|FD3F92",
            "French lilac|86608E",
            "French lime|9EFD38",
            "French mauve|D473D4",
            "French pink|FD6C9E",
            "French raspberry|C72C48",
            "French sky blue|77B5FE",
            "French violet|8806CE",
            "Frostbite|E936A7",
            "Fuchsia|FF00FF",
            "Fuchsia (Crayola)|C154C1",
            "Fulvous|E48400",
            "Fuzzy Wuzzy|87421F",
        };
        addAll(entries);

        // Basic web colors missing from the wikipedia page, then other everyday names.
        // addAll keeps the Wikipedia value when a name appears in both lists.
        addAll(new String[] {
            "Gray|808080",
            "Grey|808080",
            "Green|008000",
            "Lime|00FF00",
            "Maroon|800000",
            "Navy|000080",
            "Olive|808000",
            "Purple|800080",
            "Red|FF0000",
            "Silver|C0C0C0",
            "Teal|008080",
            "White|FFFFFF",
            "Yellow|FFFF00",

            "Orange|FFA500",
            "Pink|FFC0CB",
            "Gold|FFD700",
            "Violet|EE82EE",
            "Indigo|4B0082",
            "Magenta|FF00FF",
            "Turquoise|40E0D0",
            "Lavender|E6E6FA",
            "Salmon|FA8072",
            "Tan|D2B48C",
            "Sky blue|87CEEB",
            "Hot pink|FF69B4",
            "Chocolate|D2691E",
            "Peach|FFE5B4",
            "Mint|3EB489",
            "Khaki|F0E68C",
            "Plum|DDA0DD",
            "Scarlet|FF2400",
            "Ivory|FFFFF0",
            "Forest green|228B22",
            "Royal blue|4169E1",
            "Light blue|ADD8E6",
            "Light green|90EE90",
            "Dark green|006400",
            "Dark blue|00008B",
        });
    }

    private static void addAll(String[] entries) {
        for (String entry : entries) {
            int split = entry.indexOf('|');
            String name = entry.substring(0, split);
            int rgb = Integer.parseInt(entry.substring(split + 1), 16);
            String key = key(name);
            BY_KEY.putIfAbsent(key, new Color(rgb));
            NAME_BY_KEY.putIfAbsent(key, name);
        }
    }

    private NamedColors() { }

    /** True if the list knows a color by this name. */
    public static boolean contains(String name) {
        return name != null && BY_KEY.containsKey(key(name));
    }

    /** The color with this name, or null if the list has no such color. */
    public static Color get(String name) {
        return name == null ? null : BY_KEY.get(key(name));
    }

    /** The list's own spelling of the color name, or null if it has none. */
    public static String canonicalName(String name) {
        return name == null ? null : NAME_BY_KEY.get(key(name));
    }

    /**
     * The named color, but swapped for white when it is too dark to read
     * against the terminal's near-black background.
     */
    public static Color display(String name) {
        Color color = get(name);
        if (color == null) {
            return Color.WHITE;
        }
        int brightest = Math.max(color.getRed(),
            Math.max(color.getGreen(), color.getBlue()));
        return brightest < 80 ? Color.WHITE : color;
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
