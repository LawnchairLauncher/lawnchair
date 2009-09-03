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
#define PARAM_SCROLL_HANDLE_ID          4
#define PARAM_SCROLL_HANDLE_TEX_WIDTH   5
#define PARAM_SCROLL_HANDLE_TEX_HEIGHT  6

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

#define STATE_START_SCROLL_X            8
#define STATE_SELECTED_ICON_INDEX       9
#define STATE_SELECTED_ICON_TEXTURE     10

#define STATE_VISIBLE                   11

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

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

float
far_size(float sizeAt0)
{
    return sizeAt0 * (RADIUS+2) / 2; // -2 is the camera z=(z-camZ)/z
}

void
draw_page(int icon, int lastIcon, float centerAngle)
{
    int row;
    int col;

    float iconTextureWidth = ICON_WIDTH_PX / (float)ICON_TEXTURE_WIDTH_PX;
    float iconTextureHeight = ICON_HEIGHT_PX / (float)ICON_TEXTURE_HEIGHT_PX;

    float iconWidthAngle = VIEW_ANGLE * ICON_WIDTH_PX / SCREEN_WIDTH_PX;
    float columnGutterAngle = iconWidthAngle * 0.70f;

    float farIconSize = FAR_ICON_SIZE;
    float iconGutterHeight = farIconSize * 1.1f;

    float farIconTextureSize = far_size(2 * ICON_TEXTURE_WIDTH_PX / (float)SCREEN_WIDTH_PX);

    float labelWidthPx = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_WIDTH);
    float labelHeightPx = loadI32(ALLOC_PARAMS, PARAM_BUBBLE_HEIGHT);

    float normalizedLabelWidth = 2 * labelWidthPx / (float)SCREEN_WIDTH_PX;
    float farLabelWidth = far_size(normalizedLabelWidth);
    float farLabelHeight = far_size(labelHeightPx * (normalizedLabelWidth / labelWidthPx));
    float labelTextureWidth = labelWidthPx / loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_WIDTH);
    float labelTextureHeight = labelHeightPx / loadI32(ALLOC_PARAMS, PARAM_BUBBLE_BITMAP_HEIGHT);

    int selectedIconIndex = loadI32(ALLOC_STATE, STATE_SELECTED_ICON_INDEX);

    for (row=0; row<ROWS_PER_PAGE && icon<=lastIcon; row++) {
        float angle = centerAngle;
        angle -= (columnGutterAngle + iconWidthAngle) * 1.5f;

        float iconTop = (farIconSize + iconGutterHeight) * (2.0f + ICON_TOP_OFFSET)
                - row * (farIconSize + iconGutterHeight);
        float iconBottom = iconTop - farIconSize;

        float labelTop = iconBottom - (.1 * farLabelHeight);
        float labelBottom = labelTop - farLabelHeight;

        float iconTextureTop = iconTop + (0.5f * (farIconTextureSize - farIconSize));
        float iconTextureBottom = iconTextureTop - farIconTextureSize;

        for (col=0; col<COLUMNS_PER_PAGE && icon<=lastIcon; col++) {
            // icon
            float sine = sinf(angle);
            float cosine = cosf(angle);

            float centerX = sine * RADIUS;
            float centerZ = cosine * RADIUS;

            float iconLeftX = centerX  - (cosine * farIconTextureSize * .5);
            float iconRightX = centerX + (cosine * farIconTextureSize * .5);
            float iconLeftZ = centerZ + (sine * farIconTextureSize * .5);
            float iconRightZ = centerZ - (sine * farIconTextureSize * .5);

            if (selectedIconIndex == icon) {
                bindTexture(NAMED_PF, 0, loadI32(ALLOC_STATE, STATE_SELECTED_ICON_TEXTURE));
                drawQuadTexCoords(
                        iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                        iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                        iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                        iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);
            }

            bindTexture(NAMED_PF, 0, loadI32(ALLOC_ICON_IDS, icon));
            drawQuadTexCoords(
                    iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                    iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                    iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                    iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);

            // label
            float labelLeftX = centerX - farLabelWidth * 0.5f;
            float labelRightX = centerX + farLabelWidth * 0.5f;

            bindTexture(NAMED_PF, 0, loadI32(ALLOC_LABEL_IDS, icon));
            drawQuadTexCoords(
                    labelLeftX, labelTop, centerZ,       0.0f, 0.0f,
                    labelRightX, labelTop, centerZ,      labelTextureWidth, 0.0f,
                    labelRightX, labelBottom, centerZ,   labelTextureWidth, labelTextureHeight,
                    labelLeftX, labelBottom, centerZ,    0.0f, labelTextureHeight);

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

    // If we're not supposed to be showing, don't do anything.
    if (!loadI32(ALLOC_STATE, STATE_VISIBLE)) {
        return 0;
    }

    // icons & labels
    int iconCount = loadI32(ALLOC_STATE, STATE_ICON_COUNT);
    int pageCount = count_pages(iconCount);

    float scrollXPx = loadI32(ALLOC_STATE, STATE_SCROLL_X);
    float maxScrollXPx = -(pageCount-1) * SCREEN_WIDTH_PX;
    int done = 0;

    // Clamp -- because java doesn't know how big the icons are
    if (scrollXPx > 0) {
        scrollXPx = 0;
    }
    if (scrollXPx < maxScrollXPx) {
        scrollXPx = maxScrollXPx;
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
            if (endPos < maxScrollXPx) {
                endPos = maxScrollXPx;
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
    if (scrollXPx < maxScrollXPx) {
        scrollXPx = maxScrollXPx;
    }
    
    storeI32(ALLOC_STATE, STATE_CURRENT_SCROLL_X, scrollXPx);
    if (done) {
        storeI32(ALLOC_STATE, STATE_SCROLL_X, scrollXPx);
        storeI32(ALLOC_STATE, STATE_FLING_VELOCITY_X, 0);
        storeF(ALLOC_STATE, STATE_ADJUSTED_DECELERATION, 0);
    }

    // Draw the icons ========================================
    bindProgramVertex(NAMED_PV);
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
    
    // Draw the border lines for debugging ========================================
    /*
    bindProgramVertex(NAMED_PVOrtho);
    bindProgramFragment(NAMED_PFText);
    bindProgramFragmentStore(NAMED_PFSText);

    color(1.0f, 1.0f, 0.0f, 0.99f);
    int i;
    for (i=0; i<ROWS_PER_PAGE+1; i++) {
        int y = loadI32(ALLOC_Y_BORDERS, i);
        drawRect(0, y, SCREEN_WIDTH_PX, y+1, 0.0f);
    }
    for (i=0; i<COLUMNS_PER_PAGE+1; i++) {
        int x = loadI32(ALLOC_X_BORDERS, i);
        drawRect(x, 0, x+1, SCREEN_HEIGHT_PX, 0.0f);
    }
    */

    // Draw the scroll handle ========================================
    /*
    bindTexture(NAMED_PFText, 0, loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_ID));
    float handleLeft = 40 + (320 * (scrollXPx/(float)(maxScrollXPx)));
    float handleTop = 680;
    float handleWidth = loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_TEX_WIDTH);
    float handleHeight = loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_TEX_HEIGHT);
    drawRect(handleLeft, handleTop, handleLeft+handleWidth, handleTop+handleHeight, 0.0f);
    */

    return !done;
}

