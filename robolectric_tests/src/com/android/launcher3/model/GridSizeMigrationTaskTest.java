package com.android.launcher3.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FlagOverrideRule;
import com.android.launcher3.config.FlagOverrideRule.FlagOverride;
import com.android.launcher3.model.GridSizeMigrationTask.MultiStepMigrationTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.TestLauncherProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Unit tests for {@link GridSizeMigrationTask}
 */
@RunWith(RobolectricTestRunner.class)
public class GridSizeMigrationTaskTest {

    private static final int DESKTOP = LauncherSettings.Favorites.CONTAINER_DESKTOP;
    private static final int HOTSEAT = LauncherSettings.Favorites.CONTAINER_HOTSEAT;

    private static final int APPLICATION = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    private static final int SHORTCUT = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

    private static final String TEST_PACKAGE = "com.android.launcher3.validpackage";

    @Rule
    public final FlagOverrideRule flags = new FlagOverrideRule();

    private HashSet<String> mValidPackages;
    private InvariantDeviceProfile mIdp;
    private Context mContext;
    private TestLauncherProvider mProvider;

    @Before
    public void setUp() {

        mValidPackages = new HashSet<>();
        mValidPackages.add(TEST_PACKAGE);
        mIdp = new InvariantDeviceProfile();
        mContext = RuntimeEnvironment.application;

        mProvider = Robolectric.setupContentProvider(TestLauncherProvider.class);
        ShadowContentResolver.registerProviderInternal(LauncherProvider.AUTHORITY, mProvider);
    }

    @Test
    public void testHotseatMigration_apps_dropped() throws Exception {
        int[] hotseatItems = {
                addItem(APPLICATION, 0, HOTSEAT, 0, 0),
                addItem(SHORTCUT, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(APPLICATION, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mIdp, mValidPackages, 5, 3)
                .migrateHotseat();
        // First item is dropped as it has the least weight.
        verifyHotseat(hotseatItems[1], hotseatItems[3], hotseatItems[4]);
    }

    @Test
    public void testHotseatMigration_shortcuts_dropped() throws Exception {
        int[] hotseatItems = {
                addItem(APPLICATION, 0, HOTSEAT, 0, 0),
                addItem(30, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(10, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mIdp, mValidPackages, 5, 3)
                .migrateHotseat();
        // First item is dropped as it has the least weight.
        verifyHotseat(hotseatItems[1], hotseatItems[3], hotseatItems[4]);
    }

    private void verifyHotseat(int... sortedIds) {
        int screenId = 0;
        int total = 0;

        for (int id : sortedIds) {
            Cursor c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                    new String[]{LauncherSettings.Favorites._ID},
                    "container=-101 and screen=" + screenId, null, null, null);

            if (id == -1) {
                assertEquals(0, c.getCount());
            } else {
                assertEquals(1, c.getCount());
                c.moveToNext();
                assertEquals(id, c.getLong(0));
                total ++;
            }
            c.close();

            screenId++;
        }

        // Verify that not other entry exist in the DB.
        Cursor c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[]{LauncherSettings.Favorites._ID},
                "container=-101", null, null, null);
        assertEquals(total, c.getCount());
        c.close();
    }

    @Test
    public void testWorkspace_empty_row_column_removed() throws Exception {
        int[][][] ids = createGrid(new int[][][]{{
                {  0,  0, -1,  1},
                {  3,  1, -1,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Column 2 and row 2 got removed.
        verifyWorkspace(new int[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }});
    }

    @Test
    public void testWorkspace_new_screen_created() throws Exception {
        int[][][] ids = createGrid(new int[][][]{{
                {  0,  0,  0,  1},
                {  3,  1,  0,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column get moved to new screen
        verifyWorkspace(new int[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }, {
                {ids[0][0][2], ids[0][1][2], -1},
        }});
    }

    @Test
    public void testWorkspace_items_merged_in_next_screen() throws Exception {
        int[][][] ids = createGrid(new int[][][]{{
                {  0,  0,  0,  1},
                {  3,  1,  0,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        },{
                {  0,  0, -1,  1},
                {  3,  1, -1,  4},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on the 3rd
        // row of the second screen
        verifyWorkspace(new int[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }, {
                {ids[1][0][0], ids[1][0][1], ids[1][0][3]},
                {ids[1][1][0], ids[1][1][1], ids[1][1][3]},
                {ids[0][0][2], ids[0][1][2], -1},
        }});
    }

    @Test
    public void testWorkspace_items_not_merged_in_next_screen() throws Exception {
        // First screen has 2 items that need to be moved, but second screen has only one
        // empty space after migration (top-left corner)
        int[][][] ids = createGrid(new int[][][]{{
                {  0,  0,  0,  1},
                {  3,  1,  0,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        },{
                { -1,  0, -1,  1},
                {  3,  1, -1,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on a new screen.
        verifyWorkspace(new int[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }, {
                {          -1, ids[1][0][1], ids[1][0][3]},
                {ids[1][1][0], ids[1][1][1], ids[1][1][3]},
                {ids[1][3][0], ids[1][3][1], ids[1][3][3]},
        }, {
                {ids[0][0][2], ids[0][1][2], -1},
        }});
    }

    @FlagOverride(key = "QSB_ON_FIRST_SCREEN", value = true)
    @Test
    public void testWorkspace_first_row_blocked() throws Exception {
        // The first screen has one item on the 4th column which needs moving, as the first row
        // will be kept empty.
        int[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  3,  1,  7,  0},
                {  8,  7,  7, -1},
                {  5,  2,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 4)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on a new screen.
        verifyWorkspace(new int[][][] {{
                {          -1,           -1,           -1},
                {ids[0][1][0], ids[0][1][1], ids[0][1][2]},
                {ids[0][2][0], ids[0][2][1], ids[0][2][2]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][2]},
        }, {
                {ids[0][1][3]},
        }});
    }

    @FlagOverride(key = "QSB_ON_FIRST_SCREEN", value = true)
    @Test
    public void testWorkspace_items_moved_to_empty_first_row() throws Exception {
        // Items will get moved to the next screen to keep the first screen empty.
        int[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  0,  1,  0,  0},
                {  8,  7,  7, -1},
                {  5,  6,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on a new screen.
        verifyWorkspace(new int[][][] {{
                {          -1,           -1,           -1},
                {ids[0][2][0], ids[0][2][1], ids[0][2][2]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][2]},
        }, {
                {ids[0][1][1], ids[0][1][0], ids[0][1][2]},
                {ids[0][1][3]},
        }});
    }

    private int[][][] createGrid(int[][][] typeArray) throws Exception {
        return createGrid(typeArray, 1);
    }

    /**
     * Initializes the DB with dummy elements to represent the provided grid structure.
     * @param typeArray A 3d array of item types. {@see #addItem(int, long, long, int, int)} for
     *                  type definitions. The first dimension represents the screens and the next
     *                  two represent the workspace grid.
     * @return the same grid representation where each entry is the corresponding item id.
     */
    private int[][][] createGrid(int[][][] typeArray, int startScreen) throws Exception {
        LauncherSettings.Settings.call(mContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        int[][][] ids = new int[typeArray.length][][];

        for (int i = 0; i < typeArray.length; i++) {
            // Add screen to DB
            int screenId = startScreen + i;

            // Keep the screen id counter up to date
            LauncherSettings.Settings.call(mContext.getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

            ContentValues v = new ContentValues();
            v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
            v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
            mContext.getContentResolver().insert(LauncherSettings.WorkspaceScreens.CONTENT_URI, v);

            ids[i] = new int[typeArray[i].length][];
            for (int y = 0; y < typeArray[i].length; y++) {
                ids[i][y] = new int[typeArray[i][y].length];
                for (int x = 0; x < typeArray[i][y].length; x++) {
                    if (typeArray[i][y][x] < 0) {
                        // Empty cell
                        ids[i][y][x] = -1;
                    } else {
                        ids[i][y][x] = addItem(typeArray[i][y][x], screenId, DESKTOP, x, y);
                    }
                }
            }
        }

        IntArray allScreens = LauncherModel.loadWorkspaceScreensDb(mContext);
        return ids;
    }

    /**
     * Verifies that the workspace items are arranged in the provided order.
     * @param ids A 3d array where the first dimension represents the screen, and the rest two
     *            represent the workspace grid.
     */
    private void verifyWorkspace(int[][][] ids) {
        IntArray allScreens = LauncherModel.loadWorkspaceScreensDb(mContext);
        assertEquals(ids.length, allScreens.size());
        int total = 0;

        for (int i = 0; i < ids.length; i++) {
            int screenId = allScreens.get(i);
            for (int y = 0; y < ids[i].length; y++) {
                for (int x = 0; x < ids[i][y].length; x++) {
                    int id = ids[i][y][x];

                    Cursor c = mContext.getContentResolver().query(
                            LauncherSettings.Favorites.CONTENT_URI,
                            new String[]{LauncherSettings.Favorites._ID},
                            "container=-100 and screen=" + screenId +
                                    " and cellX=" + x + " and cellY=" + y, null, null, null);
                    if (id == -1) {
                        assertEquals(0, c.getCount());
                    } else {
                        assertEquals(1, c.getCount());
                        c.moveToNext();
                        assertEquals(String.format("Failed to verify item at %d %d, %d", i, y, x),
                                id, c.getLong(0));
                        total++;
                    }
                    c.close();
                }
            }
        }

        // Verify that not other entry exist in the DB.
        Cursor c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[]{LauncherSettings.Favorites._ID},
                "container=-100", null, null, null);
        assertEquals(total, c.getCount());
        c.close();
    }

    /**
     * Adds a dummy item in the DB.
     * @param type {@link #APPLICATION} or {@link #SHORTCUT} or >= 2 for
     *             folder (where the type represents the number of items in the folder).
     */
    private int addItem(int type, int screen, int container, int x, int y) throws Exception {
        int id = LauncherSettings.Settings.call(mContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);

        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites._ID, id);
        values.put(LauncherSettings.Favorites.CONTAINER, container);
        values.put(LauncherSettings.Favorites.SCREEN, screen);
        values.put(LauncherSettings.Favorites.CELLX, x);
        values.put(LauncherSettings.Favorites.CELLY, y);
        values.put(LauncherSettings.Favorites.SPANX, 1);
        values.put(LauncherSettings.Favorites.SPANY, 1);

        if (type == APPLICATION || type == SHORTCUT) {
            values.put(LauncherSettings.Favorites.ITEM_TYPE, type);
            values.put(LauncherSettings.Favorites.INTENT,
                    new Intent(Intent.ACTION_MAIN).setPackage(TEST_PACKAGE).toUri(0));
        } else {
            values.put(LauncherSettings.Favorites.ITEM_TYPE,
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
            // Add folder items.
            for (int i = 0; i < type; i++) {
                addItem(APPLICATION, 0, id, 0, 0);
            }
        }

        mContext.getContentResolver().insert(LauncherSettings.Favorites.CONTENT_URI, values);
        return id;
    }

    @Test
    public void testMultiStepMigration_small_to_large() throws Exception {
        MultiStepMigrationTaskVerifier verifier = new MultiStepMigrationTaskVerifier();
        verifier.migrate(new Point(3, 3), new Point(5, 5));
        verifier.assertCompleted();
    }

    @Test
    public void testMultiStepMigration_large_to_small() throws Exception {
        MultiStepMigrationTaskVerifier verifier = new MultiStepMigrationTaskVerifier(
                5, 5, 4, 4,
                4, 4, 3, 4
        );
        verifier.migrate(new Point(5, 5), new Point(3, 4));
        verifier.assertCompleted();
    }

    @Test
    public void testMultiStepMigration_zig_zag() throws Exception {
        MultiStepMigrationTaskVerifier verifier = new MultiStepMigrationTaskVerifier(
                5, 7, 4, 7,
                4, 7, 3, 7
        );
        verifier.migrate(new Point(5, 5), new Point(3, 7));
        verifier.assertCompleted();
    }

    private static class MultiStepMigrationTaskVerifier extends MultiStepMigrationTask {

        private final LinkedList<Point> mPoints;

        public MultiStepMigrationTaskVerifier(int... points) {
            super(null, null);

            mPoints = new LinkedList<>();
            for (int i = 0; i < points.length; i += 2) {
                mPoints.add(new Point(points[i], points[i + 1]));
            }
        }

        @Override
        protected boolean runStepTask(Point sourceSize, Point nextSize) throws Exception {
            assertEquals(sourceSize, mPoints.poll());
            assertEquals(nextSize, mPoints.poll());
            return false;
        }

        public void assertCompleted() {
            assertTrue(mPoints.isEmpty());
        }
    }
}
