#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PF)
#pragma stateFragmentStore(PFS)

#define PI 3.14159f

// Variables from java ======

// Parameters ======
#define PARAM_BUBBLE_WIDTH              0
#define PARAM_BUBBLE_HEIGHT             1
#define PARAM_BUBBLE_BITMAP_WIDTH       2
#define PARAM_BUBBLE_BITMAP_HEIGHT      3

// State ======
#define STATE_ICON_COUNT                0
#define STATE_SCROLL_X                  1
#define STATE_FLING_TIME                2
#define STATE_FLING_VELOCITY_X          3
#define STATE_ADJUSTED_DECELERATION     4

/* with fling offset applied */
#define STATE_CURRENT_SCROLL_X          5

#define STATE_FLING_DURATION            6
#define STATE_FLING_END_POS             7

// Scratch variables ======
#define SCRATCH_ADJUSTED_DECELERATION   0

// Drawing constants, should be parameters ======
#define SCREEN_WIDTH_PX 480
#define SCREEN_HEIGHT 854
#define COLUMNS_PER_PAGE 4
#define ROWS_PER_PAGE 4

#define PAGE_PADDING_TOP_PX 80
#define CELL_PADDING_TOP_PX 5
#define ICON_HEIGHT_PX 64
#define ICON_TEXTURE_HEIGHT_PX 128
#define ICON_LABEL_GUTTER_PX 5
#define CELL_PADDING_BOTTOM_PX 5
#define ROW_GUTTER_PX 10

#define PAGE_PADDING_LEFT_PX 22
#define CELL_WIDTH_PX 105
#define ICON_WIDTH_PX 64
#define ICON_TEXTURE_WIDTH_PX 128

#define VIEW_ANGLE 1.28700222f
#define RADIUS 4.0f

int
count_pages(int iconCount)
{
    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    int pages = iconCount / iconsPerPage;
    if (pages*iconsPerPage != iconCount) {
        pages++;
    }
    return pages;
}

float
modf(float x, float y)
{
    return x-(y*floorf(x/y));
}

void
draw_page(int icon, int lastIcon, float centerAngle)
{
    int row;
    int col;

    float iconTextureWidth = ICON_WIDTH_PX / (float)ICON_TEXTURE_WIDTH_PX;
    float iconTextureHeight = ICON_HEIGHT_PX / (float)ICON_TEXTURE_HEIGHT_PX;

    float iconWidthAngle = VIEW_ANGLE * ICON_WIDTH_PX / SCREEN_WIDTH_PX;
    float columnGutterAngle = iconWidthAngle * 0.5f;

    float normalizedIconSize = 2 * ICON_WIDTH_PX / (float)SCREEN_WIDTH_PX;
    float farIconSize = normalizedIconSize * (RADIUS+2) / 2; // -2 is the camera z=(z-camZ)/z

    for (row=0; row<ROWS_PER_PAGE && icon<=lastIcon; row++) {
        float angle = centerAngle;
        angle -= (columnGutterAngle + iconWidthAngle) * 1.5f;

        float iconTop = (farIconSize + (.5*farIconSize)) * 1.5
                - row * (farIconSize + (.5*farIconSize));
        float iconBottom = iconTop - farIconSize;

        for (col=0; col<COLUMNS_PER_PAGE && icon<=lastIcon; col++) {
            // icon
            float sine = sinf(angle);
            float cosine = cosf(angle);

            float iconLeftX = sine * RADIUS  - (cosine * farIconSize * .5);
            float iconRightX = iconLeftX + (cosine * farIconSize);
            float iconLeftZ = (cosine * RADIUS) + (sine * farIconSize * .5);
            float iconRightZ = (iconLeftZ - (sine * farIconSize));

            bindTexture(NAMED_PF, 0, loadI32(ALLOC_ICON_IDS, icon));
            drawQuadTexCoords(
                    iconLeftX, iconTop, iconLeftZ,       0.0f, 0.0f,
                    iconRightX, iconTop, iconRightZ,     iconTextureWidth, 0.0f,
                    iconRightX, iconBottom, iconRightZ,  iconTextureWidth, iconTextureHeight,
                    iconLeftX, iconBottom, iconLeftZ,    0.0f, iconTextureHeight);

            // label
            /*
            float labelLeft = s + ((cellWidth-labelWidth)/2.0f);
            float labelTop = iconTop - iconHeight - iconLabelGutter;

            bindProgramFragment(NAMED_PFText);
            bindProgramFragmentStore(NAMED_PFSText);

            bindTexture(NAMED_PFText, 0, loadI32(ALLOC_LABEL_IDS, icon));
            drawRect(labelLeft, labelTop, labelLeft+labelTextureWidth,
                    labelTop-labelTextureHeight, 0.0f);
            */
            angle += columnGutterAngle + iconWidthAngle;
            icon++;
        }
    }
}

int
main(int launchID)
{
    // Clear to transparent
    pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    // icons & labels
    int iconCount = loadI32(ALLOC_STATE, STATE_ICON_COUNT);
    int pageCount = count_pages(iconCount);

    float densityScale = 2.0f / SCREEN_WIDTH_PX;
    float screenTop = SCREEN_HEIGHT/(float)SCREEN_WIDTH_PX; // == (SCREEN_HEIGHT/2)*densityScale;

    float pagePaddingTop = screenTop - (PAGE_PADDING_TOP_PX * densityScale);
    float pageGutterY = ROW_GUTTER_PX * densityScale;
    float cellHeight = (CELL_PADDING_TOP_PX + ICON_HEIGHT_PX + ICON_LABEL_GUTTER_PX
            + loadI32(ALLOC_PARAMS, PARAM_BUBBLE_HEIGHT)
            + CELL_PADDING_BOTTOM_PX + ROW_GUTTER_PX) * densityScale;
    float cellPaddingTop = CELL_PADDING_TOP_PX * densityScale;
    float iconLabelGutter = ICON_LABEL_GUTTER_PX * densityScale;

    float labelWidth = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_WIDTH) * densityScale;
    float labelTextureWidth = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_WIDTH) * densityScale;
    float labelTextureHeight = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_HEIGHT) * densityScale;

    float scrollXPx = loadI32(ALLOC_STATE, STATE_SCROLL_X);
    float maxScrollX = -(pageCount-1) * SCREEN_WIDTH_PX;
    int done = 0;

    // Clamp -- because java doesn't know how big the icons are
    if (scrollXPx > 0) {
        scrollXPx = 0;
    }
    if (scrollXPx < maxScrollX) {
        scrollXPx = maxScrollX;
    }

    // If we've been given a velocity, start a fling
    float flingVelocityPxMs = loadI32(ALLOC_STATE, STATE_FLING_VELOCITY_X);
    if (flingVelocityPxMs != 0) {
        // how many screens will this velocity do? TODO: use long
        // G * ppi * friction // why G? // friction = 0.015
        float deceleration = loadF(ALLOC_STATE, STATE_ADJUSTED_DECELERATION);
        float flingDurationMs;
        if (deceleration == 0) {
            // On the first frame, calculate which animation we're going to do.  If it's
            // going to end up less than halfway into a page, we'll bounce back the previous
            // page.  Otherwise, we'll adjust the deceleration so it just makes it to the
            // page boundary.
            if (flingVelocityPxMs > 0) {
                deceleration = -1000;
            } else {
                deceleration = 1000;
            }
            // minimum velocity
            if (flingVelocityPxMs < 0) {
                if (flingVelocityPxMs > -500) {
                    flingVelocityPxMs = -500;
                }
            } else {
                if (flingVelocityPxMs < 500) {
                    flingVelocityPxMs = 500;
                }
            }

            // v' = v + at --> t = -v / a
            // x' = x + vt + .5 a t^2
            flingDurationMs = - flingVelocityPxMs / deceleration;
            float endPos = scrollXPx + (flingVelocityPxMs*flingDurationMs)
                    + ((deceleration*flingDurationMs*flingDurationMs)/2);

            if (endPos > 0) {
                endPos = 0;
            }
            if (endPos < maxScrollX) {
                endPos = maxScrollX;
            }
            float scrollOnPage = modf(endPos, SCREEN_WIDTH_PX);
            int endPage = -endPos/SCREEN_WIDTH_PX;

            if (flingVelocityPxMs < 0) {
                if (scrollOnPage < (SCREEN_WIDTH_PX/2)) {
                    // adjust the deceleration so we align on the page boundary
                    // a = 2(x-x0-v0t)/t^2
                    endPos = -(endPage+1) * SCREEN_WIDTH_PX;
                    debugI32("endPos case 1", endPos);
                } else {
                    // TODO: bounce
                    endPos = -(endPage+1) * SCREEN_WIDTH_PX;
                    debugI32("endPos case 2", endPos);
                }
            } else {
                if (scrollOnPage >= (SCREEN_WIDTH_PX/2)) {
                    // adjust the deceleration so we align on the page boundary
                    endPos = -endPage * SCREEN_WIDTH_PX;
                    debugI32("endPos case 3", endPos);
                } else {
                    // TODO: bounce
                    endPos = -endPage * SCREEN_WIDTH_PX;
                    debugI32("endPos case 4", endPos);
                }
            }
            // v = v0 + at --> (v - v0) / t
            deceleration = 2*(endPos-scrollXPx-(flingVelocityPxMs*flingDurationMs))
                    / (flingDurationMs*flingDurationMs);
            endPos = scrollXPx + (flingVelocityPxMs*flingDurationMs)
                    + ((deceleration*flingDurationMs*flingDurationMs)/2);

            storeF(ALLOC_STATE, STATE_ADJUSTED_DECELERATION, deceleration);
            storeF(ALLOC_STATE, STATE_FLING_DURATION, flingDurationMs);
            storeF(ALLOC_STATE, STATE_FLING_END_POS, endPos);
        } else {
            flingDurationMs = loadF(ALLOC_STATE, STATE_FLING_DURATION);
        }

        // adjust the deceleration so we always hit a page boundary

        int flingTime = loadI32(ALLOC_STATE, STATE_FLING_TIME);
        int now = uptimeMillis();
        float elapsedTime = (now - flingTime) / 1000.0f;
        int animEndTime = -flingVelocityPxMs / deceleration;

        int flingOffsetPx = (flingVelocityPxMs * elapsedTime)
                + (deceleration * elapsedTime * elapsedTime / 2.0f);
        scrollXPx += flingOffsetPx;

        if (elapsedTime > flingDurationMs) {
            scrollXPx = loadF(ALLOC_STATE, STATE_FLING_END_POS);
            done = 1;
        }
    }

    // Clamp
    if (scrollXPx > 0) {
        scrollXPx = 0;
    }
    if (scrollXPx < maxScrollX) {
        scrollXPx = maxScrollX;
    }
    
    storeI32(ALLOC_STATE, STATE_CURRENT_SCROLL_X, scrollXPx);
    if (done) {
        storeI32(ALLOC_STATE, STATE_SCROLL_X, scrollXPx);
        storeI32(ALLOC_STATE, STATE_FLING_VELOCITY_X, 0);
        storeF(ALLOC_STATE, STATE_ADJUSTED_DECELERATION, 0);
    }

    bindProgramFragment(NAMED_PF);
    bindProgramFragmentStore(NAMED_PFS);

    // Bug makes 1.0f alpha fail.
    color(1.0f, 1.0f, 1.0f, 0.99f);

    int lastIcon = iconCount-1;

    float currentPage = -scrollXPx / (float)SCREEN_WIDTH_PX;
    int page = currentPage;
    float currentPagePosition = currentPage - page;

    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    int icon = clamp(iconsPerPage * page, 0, lastIcon);

    draw_page(icon, lastIcon, -VIEW_ANGLE*currentPagePosition);
    draw_page(icon+iconsPerPage, lastIcon, (-VIEW_ANGLE*currentPagePosition)+VIEW_ANGLE);

    return !done;
}

