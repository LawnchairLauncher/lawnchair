package com.android.launcher3.model;

import static com.android.launcher3.model.GridSizeMigrationTask.getWorkspaceScreenIds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.graphics.Point;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.FlagOverrideRule;
import com.android.launcher3.model.GridSizeMigrationTask.MultiStepMigrationTask;
import com.android.launcher3.util.IntArray;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Unit tests for {@link GridSizeMigrationTask}
 */
@RunWith(RobolectricTestRunner.class)
public class GridSizeMigrationTaskTest extends BaseGridChangesTestCase {

    @Rule
    public final FlagOverrideRule flags = new FlagOverrideRule();

    private HashSet<String> mValidPackages;
    private InvariantDeviceProfile mIdp;

    @Before
    public void setUp() {
        mValidPackages = new HashSet<>();
        mValidPackages.add(TEST_PACKAGE);
        mIdp = new InvariantDeviceProfile();
    }

    @Test
    public void testHotseatMigration_apps_dropped() throws Exception {
        int[] hotseatItems = {
                addItem(APP_ICON, 0, HOTSEAT, 0, 0),
                addItem(SHORTCUT, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(APP_ICON, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mDb, mValidPackages, 5, 3)
                .migrateHotseat();
        // First item is dropped as it has the least weight.
        verifyHotseat(hotseatItems[1], hotseatItems[3], hotseatItems[4]);
    }

    @Test
    public void testHotseatMigration_shortcuts_dropped() throws Exception {
        int[] hotseatItems = {
                addItem(APP_ICON, 0, HOTSEAT, 0, 0),
                addItem(30, 1, HOTSEAT, 0, 0),
                -1,
                addItem(SHORTCUT, 3, HOTSEAT, 0, 0),
                addItem(10, 4, HOTSEAT, 0, 0),
        };

        mIdp.numHotseatIcons = 3;
        new GridSizeMigrationTask(mContext, mDb, mValidPackages, 5, 3)
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

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

    @Test
    public void testWorkspace_first_row_blocked() throws Exception {
        if (!FeatureFlags.QSB_ON_FIRST_SCREEN) {
            return;
        }
        // The first screen has one item on the 4th column which needs moving, as the first row
        // will be kept empty.
        int[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  3,  1,  7,  0},
                {  8,  7,  7, -1},
                {  5,  2,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

    @Test
    public void testWorkspace_items_moved_to_empty_first_row() throws Exception {
        if (!FeatureFlags.QSB_ON_FIRST_SCREEN) {
            return;
        }
        // Items will get moved to the next screen to keep the first screen empty.
        int[][][] ids = createGrid(new int[][][]{{
                { -1, -1, -1, -1},
                {  0,  1,  0,  0},
                {  8,  7,  7, -1},
                {  5,  6,  7, -1},
        }}, 0);

        new GridSizeMigrationTask(mContext, mDb, mValidPackages,
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

    /**
     * Verifies that the workspace items are arranged in the provided order.
     * @param ids A 3d array where the first dimension represents the screen, and the rest two
     *            represent the workspace grid.
     */
    private void verifyWorkspace(int[][][] ids) {
        IntArray allScreens = getWorkspaceScreenIds(mDb);
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
            super(null, null, null);

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
