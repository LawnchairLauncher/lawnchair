#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PF)
#pragma stateFragmentStore(PFS)

#define PI 3.14159f

// Allocations ======
#define ALLOC_PARAMS    0
#define ALLOC_STATE     1
#define ALLOC_SCRATCH   2
#define ALLOC_ICON_IDS  3
#define ALLOC_LABEL_IDS 4

// Variables from java ======

// Parameters ======
#define PARAM_BUBBLE_WIDTH              0
#define PARAM_BUBBLE_HEIGHT             1
#define PARAM_BUBBLE_BITMAP_WIDTH       2
#define PARAM_BUBBLE_BITMAP_HEIGHT      3

//#define STATE_POS_X             0
#define STATE_DONE              1
//#define STATE_PRESSURE          2
#define STATE_ZOOM              3
//#define STATE_WARP              4
#define STATE_ORIENTATION       5
#define STATE_SELECTION         6
#define STATE_FIRST_VISIBLE     7
#define STATE_COUNT             8
#define STATE_TOUCH             9

// Scratch variables ======
#define SCRATCH_FADE 0
#define SCRATCH_ZOOM 1
#define SCRATCH_ROT 2

// Drawing constants, should be parameters ======
#define SCREEN_WIDTH 480
#define SCREEN_HEIGHT 854
#define COLUMNS_PER_PAGE 4
#define ROWS_PER_PAGE 4
#define DIAMETER 8.0f

#define PAGE_PADDING_TOP_PX 80
#define CELL_PADDING_TOP_PX 5
#define ICON_HEIGHT_PX 64
#define ICON_LABEL_GUTTER_PX 5
#define CELL_PADDING_BOTTOM_PX 5
#define ROW_GUTTER_PX 10

#define PAGE_PADDING_LEFT_PX 22
#define CELL_WIDTH_PX 105
#define ICON_WIDTH_PX 64
#define COLUMN_GUTTER_PX 5
#define LABEL_WIDTH_PX 105

int
count_pages(int iconCount)
{
    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    int pages = iconCount / iconsPerPage;
    if (pages*iconsPerPage != iconCount) {
        iconCount++;
    }
    return iconCount;
}

int
main(void* con, int ft, int launchID)
{
    // Clear to transparent
    pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    // icons & labels
    int iconCount = loadI32(ALLOC_STATE, STATE_COUNT);
    int pageCount = count_pages(iconCount);

    float densityScale = 2.0f / SCREEN_WIDTH;
    float screenTop = SCREEN_HEIGHT/(float)SCREEN_WIDTH; // == (SCREEN_HEIGHT/2)*densityScale;

    float pagePaddingTop = screenTop - (PAGE_PADDING_TOP_PX * densityScale);
    float pageGutterY = ROW_GUTTER_PX * densityScale;
    float cellHeight = (CELL_PADDING_TOP_PX + ICON_HEIGHT_PX + ICON_LABEL_GUTTER_PX
            + loadI32(ALLOC_PARAMS, PARAM_BUBBLE_HEIGHT)
            + CELL_PADDING_BOTTOM_PX + ROW_GUTTER_PX) * densityScale;
    float cellPaddingTop = CELL_PADDING_TOP_PX * densityScale;
    float iconHeight = ICON_HEIGHT_PX * densityScale;
    float iconLabelGutter = ICON_LABEL_GUTTER_PX * densityScale;

    float pagePaddingLeft = PAGE_PADDING_LEFT_PX * densityScale;
    float cellWidth = CELL_WIDTH_PX * densityScale;
    float iconWidth = ICON_WIDTH_PX * densityScale;
    float columnGutter = COLUMN_GUTTER_PX * densityScale;

    float pageLeft = -1;
    int icon = 0;
    int page;

    float labelWidth = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_WIDTH) * densityScale;
    float labelTextureWidth = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_WIDTH) * densityScale;
    float labelTextureHeight = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_HEIGHT) * densityScale;

    int scrollXPx = 100;
    pageLeft -= scrollXPx * densityScale;


    for (page=0; page<pageCount; page++) {
        // Bug makes 1.0f alpha fail.
        color(1.0f, 1.0f, 1.0f, 0.99f);
        
        float cellTop = pagePaddingTop;
        int row;
        for (row=0; row<ROWS_PER_PAGE && icon<iconCount; row++) {
            float s = pageLeft; // distance along the linear strip of icons in normalized coords
            s += pagePaddingLeft;
            int col;
            for (col=0; col<COLUMNS_PER_PAGE && icon<iconCount; col++) {
                // icon
                float iconLeft = s + ((cellWidth-iconWidth)/2.0f);
                float iconRight = iconLeft + iconWidth;
                float iconTop = cellTop - cellPaddingTop;
                float iconBottom = iconTop - iconHeight;

                bindProgramFragment(NAMED_PF);
                bindProgramFragmentStore(NAMED_PFS);

                bindTexture(NAMED_PF, 0, loadI32(ALLOC_ICON_IDS, icon));
                drawRect(iconLeft, iconTop, iconRight, iconBottom, 0.0f);

                // label
                float labelLeft = s + ((cellWidth-labelWidth)/2.0f);
                float labelTop = iconBottom - iconLabelGutter;

                bindProgramFragment(NAMED_PFText);
                bindProgramFragmentStore(NAMED_PFSText);

                bindTexture(NAMED_PFText, 0, loadI32(ALLOC_LABEL_IDS, icon));
                drawRect(labelLeft, labelTop, labelLeft+labelTextureWidth,
                        labelTop-labelTextureHeight, 0.0f);

                s += cellWidth + columnGutter;
                icon++;
            }
            cellTop -= cellHeight;
        }
        pageLeft += 2.0f;
    }

    return 0;
}


