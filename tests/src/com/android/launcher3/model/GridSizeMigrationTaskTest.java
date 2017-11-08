package com.android.launcher3.model;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.provider.ProviderTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.GridSizeMigrationTask.MultiStepMigrationTask;
import com.android.launcher3.util.TestLauncherProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link GridSizeMigrationTask}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class GridSizeMigrationTaskTest {

    @Rule
    public ProviderTestRule mProviderRule =
            new ProviderTestRule.Builder(TestLauncherProvider.class, LauncherProvider.AUTHORITY)
                    .build();

    private static final long DESKTOP = LauncherSettings.Favorites.CONTAINER_DESKTOP;
    private static final long HOTSEAT = LauncherSettings.Favorites.CONTAINER_HOTSEAT;

    private static final int APPLICATION = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    private static final int SHORTCUT = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

    private static final String TEST_PACKAGE = "com.android.launcher3.validpackage";
    private static final String VALID_INTENT =
            new Intent(Intent.ACTION_MAIN).setPackage(TEST_PACKAGE).toUri(0);

    private HashSet<String> mValidPackages;
    private InvariantDeviceProfile mIdp;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mValidPackages = new HashSet<>();
        mValidPackages.add(TEST_PACKAGE);

        mIdp = new InvariantDeviceProfile();

        mContext = new ContextWrapper(InstrumentationRegistry.getTargetContext()) {

            @Override
            public ContentResolver getContentResolver() {
                return mProviderRule.getResolver();
            }
        };
    }

    @Test
    public void testHotseatMigration_apps_dropped() throws Exception {
        long[] hotseatItems = {
                addItem(APPLICATION, 0, HOTSEAT, 0, 0),
                addItem(SHORTCUT, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(APPLICATION, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mIdp, mValidPackages, 5, 3)
                .migrateHotseat();
        if (FeatureFlags.NO_ALL_APPS_ICON) {
            // First item is dropped as it has the least weight.
            verifyHotseat(hotseatItems[1], hotseatItems[3], hotseatItems[4]);
        } else {
            // First & last items are dropped as they have the least weight.
            verifyHotseat(hotseatItems[1], -1, hotseatItems[3]);
        }
    }

    @Test
    public void testHotseatMigration_shortcuts_dropped() throws Exception {
        long[] hotseatItems = {
                addItem(APPLICATION, 0, HOTSEAT, 0, 0),
                addItem(30, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(10, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mIdp, mValidPackages, 5, 3)
                .migrateHotseat();
        if (FeatureFlags.NO_ALL_APPS_ICON) {
            // First item is dropped as it has the least weight.
            verifyHotseat(hotseatItems[1], hotseatItems[3], hotseatItems[4]);
        } else {
            // First & third items are dropped as they have the least weight.
            verifyHotseat(hotseatItems[1], -1, hotseatItems[4]);
        }
    }

    private void verifyHotseat(long... sortedIds) {
        int screenId = 0;
        int total = 0;

        for (long id : sortedIds) {
            Cursor c = mProviderRule.getResolver().query(LauncherSettings.Favorites.CONTENT_URI,
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
        Cursor c = mProviderRule.getResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[]{LauncherSettings.Favorites._ID},
                "container=-101", null, null, null);
        assertEquals(total, c.getCount());
        c.close();
    }

    @Test
    public void testWorkspace_empty_row_column_removed() throws Exception {
        long[][][] ids = createGrid(new int[][][]{{
                {  0,  0, -1,  1},
                {  3,  1, -1,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Column 2 and row 2 got removed.
        verifyWorkspace(new long[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }});
    }

    @Test
    public void testWorkspace_new_screen_created() throws Exception {
        long[][][] ids = createGrid(new int[][][]{{
                {  0,  0,  0,  1},
                {  3,  1,  0,  4},
                { -1, -1, -1, -1},
                {  5,  2, -1,  6},
        }});

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column get moved to new screen
        verifyWorkspace(new long[][][] {{
                {ids[0][0][0], ids[0][0][1], ids[0][0][3]},
                {ids[0][1][0], ids[0][1][1], ids[0][1][3]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][3]},
        }, {
                {ids[0][0][2], ids[0][1][2], -1},
        }});
    }

    @Test
    public void testWorkspace_items_merged_in_next_screen() throws Exception {
        long[][][] ids = createGrid(new int[][][]{{
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
        verifyWorkspace(new long[][][] {{
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
        long[][][] ids = createGrid(new int[][][]{{
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
        verifyWorkspace(new long[][][] {{
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

    @Test
    public void testWorkspace_first_row_blocked() throws Exception {
        // The first screen has one item on the 4th column which needs moving, as the first row
        // will be kept empty.
        long[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  3,  1,  7,  0},
                {  8,  7,  7, -1},
                {  5,  2,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 4)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on a new screen.
        verifyWorkspace(new long[][][] {{
                {          -1,           -1,           -1},
                {ids[0][1][0], ids[0][1][1], ids[0][1][2]},
                {ids[0][2][0], ids[0][2][1], ids[0][2][2]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][2]},
        }, {
                {ids[0][1][3]},
        }});
    }

    @Test
    public void testWorkspace_items_moved_to_empty_first_row() throws Exception {
        // Items will get moved to the next screen to keep the first screen empty.
        long[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  0,  1,  0,  0},
                {  8,  7,  7, -1},
                {  5,  6,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mIdp, mValidPackages,
                new Point(4, 4), new Point(3, 3)).migrateWorkspace();

        // Items in the second column of the first screen should get placed on a new screen.
        verifyWorkspace(new long[][][] {{
                {          -1,           -1,           -1},
                {ids[0][2][0], ids[0][2][1], ids[0][2][2]},
                {ids[0][3][0], ids[0][3][1], ids[0][3][2]},
        }, {
                {ids[0][1][1], ids[0][1][0], ids[0][1][2]},
                {ids[0][1][3]},
        }});
    }

    private long[][][] createGrid(int[][][] typeArray) throws Exception {
        return createGrid(typeArray, 1);
    }

    /**
     * Initializes the DB with dummy elements to represent the provided grid structure.
     * @param typeArray A 3d array of item types. {@see #addItem(int, long, long, int, int)} for
     *                  type definitions. The first dimension represents the screens and the next
     *                  two represent the workspace grid.
     * @return the same grid representation where each entry is the corresponding item id.
     */
    private long[][][] createGrid(int[][][] typeArray, long startScreen) throws Exception {
        LauncherSettings.Settings.call(mProviderRule.getResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        long[][][] ids = new long[typeArray.length][][];

        for (int i = 0; i < typeArray.length; i++) {
            // Add screen to DB
            long screenId = startScreen + i;

            // Keep the screen id counter up to date
            LauncherSettings.Settings.call(mProviderRule.getResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

            ContentValues v = new ContentValues();
            v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
            v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
            mProviderRule.getResolver().insert(LauncherSettings.WorkspaceScreens.CONTENT_URI, v);

            ids[i] = new long[typeArray[i].length][];
            for (int y = 0; y < typeArray[i].length; y++) {
                ids[i][y] = new long[typeArray[i][y].length];
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
        return ids;
    }

    /**
     * Verifies that the workspace items are arranged in the provided order.
     * @param ids A 3d array where the first dimension represents the screen, and the rest two
     *            represent the workspace grid.
     */
    private void verifyWorkspace(long[][][] ids) {
        ArrayList<Long> allScreens = LauncherModel.loadWorkspaceScreensDb(mContext);
        assertEquals(ids.length, allScreens.size());
        int total = 0;

        for (int i = 0; i < ids.length; i++) {
            long screenId = allScreens.get(i);
            for (int y = 0; y < ids[i].length; y++) {
                for (int x = 0; x < ids[i][y].length; x++) {
                    long id = ids[i][y][x];

                    Cursor c = mProviderRule.getResolver().query(
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
        Cursor c = mProviderRule.getResolver().query(LauncherSettings.Favorites.CONTENT_URI,
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
    private long addItem(int type, long screen, long container, int x, int y) throws Exception {
        long id = LauncherSettings.Settings.call(mProviderRule.getResolver(),
                LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getLong(LauncherSettings.Settings.EXTRA_VALUE);

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
            values.put(LauncherSettings.Favorites.INTENT, VALID_INTENT);
        } else {
            values.put(LauncherSettings.Favorites.ITEM_TYPE,
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
            // Add folder items.
            for (int i = 0; i < type; i++) {
                addItem(APPLICATION, 0, id, 0, 0);
            }
        }

        mProviderRule.getResolver().insert(LauncherSettings.Favorites.CONTENT_URI, values);
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
