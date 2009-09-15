#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PF)
#pragma stateFragmentStore(PFS)

#define PI 3.14159f

float deceleration;

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

void init() {
    deceleration = 0;
}

int g_lastFrameTime = 0;
void print_frame_rate()
{
    int now = uptimeMillis();
    if (g_lastFrameTime != 0) {
        debugI32("frame_rate", 1000/(now-g_lastFrameTime));
    }
    g_lastFrameTime = now;
}

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

    float scale = 1.0f - state->zoom;

    float iconTextureWidth = ICON_WIDTH_PX / (float)ICON_TEXTURE_WIDTH_PX;
    float iconTextureHeight = ICON_HEIGHT_PX / (float)ICON_TEXTURE_HEIGHT_PX;

    float iconWidthAngle = VIEW_ANGLE * ICON_WIDTH_PX / SCREEN_WIDTH_PX;
    float columnGutterAngle = iconWidthAngle * 0.70f;

    float farIconSize = FAR_ICON_SIZE;
    float iconGutterHeight = farIconSize * 1.1f;

    float farIconTextureSize = far_size(2 * ICON_TEXTURE_WIDTH_PX / (float)SCREEN_WIDTH_PX);

    float normalizedLabelWidth = 2 * params->bubbleWidth / (float)SCREEN_WIDTH_PX;
    float farLabelWidth = far_size(normalizedLabelWidth);
    float farLabelHeight = far_size(params->bubbleHeight * (normalizedLabelWidth / params->bubbleWidth));
    float labelTextureWidth = (float)params->bubbleWidth / params->bubbleBitmapWidth;
    float labelTextureHeight = (float)params->bubbleHeight / params->bubbleBitmapHeight;

    for (row=0; row<ROWS_PER_PAGE && icon<=lastIcon; row++) {
        float angle = centerAngle;
        angle -= (columnGutterAngle + iconWidthAngle) * 1.5f;

        float iconTop = (farIconSize + iconGutterHeight) * (2.0f + ICON_TOP_OFFSET)
                - row * (farIconSize + iconGutterHeight);
        iconTop -= 6 * scale; // make the zoom point below center
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
            centerZ -= ((RADIUS+2+1)*scale); // 2 is camera loc, 1 put it slightly behind that.

            float iconLeftX = centerX  - (cosine * farIconTextureSize * .5);
            float iconRightX = centerX + (cosine * farIconTextureSize * .5);
            float iconLeftZ = centerZ + (sine * farIconTextureSize * .5);
            float iconRightZ = centerZ - (sine * farIconTextureSize * .5);

            if (state->selectedIconIndex == icon) {
                bindTexture(NAMED_PF, 0, state->selectedIconTexture);
                drawQuadTexCoords(
                        iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                        iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                        iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                        iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);
            } else {
                bindTexture(NAMED_PF, 0, loadI32(ALLOC_ICON_IDS, icon));
                drawQuadTexCoords(
                        iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                        iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                        iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                        iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);
            }

            // label
            if (scale <= 0.1f) {
                float labelLeftX = centerX - farLabelWidth * 0.5f;
                float labelRightX = centerX + farLabelWidth * 0.5f;

                bindTexture(NAMED_PF, 0, loadI32(ALLOC_LABEL_IDS, icon));
                drawQuadTexCoords(
                        labelLeftX, labelTop, centerZ,       0.0f, 0.0f,
                        labelRightX, labelTop, centerZ,      labelTextureWidth, 0.0f,
                        labelRightX, labelBottom, centerZ,   labelTextureWidth, labelTextureHeight,
                        labelLeftX, labelBottom, centerZ,    0.0f, labelTextureHeight);
            }

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
    if (!state->visible) {
        return 0;
    }

    // icons & labels
    int iconCount = state->iconCount;
    int pageCount = count_pages(iconCount);

    float scrollXPx = state->scrollX;
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
    float flingVelocityPxMs = state->flingVelocityX;
    if (flingVelocityPxMs != 0) {
        // how many screens will this velocity do? TODO: use long
        // G * ppi * friction // why G? // friction = 0.015
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

            state->flingDuration = flingDurationMs;
            state->flingEndPos = endPos;
        } else {
            flingDurationMs = state->flingDuration;
        }

        // adjust the deceleration so we always hit a page boundary

        int now = uptimeMillis();
        float elapsedTime = (now - state->flingTimeMs) / 1000.0f;
        int animEndTime = -flingVelocityPxMs / deceleration;

        int flingOffsetPx = (flingVelocityPxMs * elapsedTime)
                + (deceleration * elapsedTime * elapsedTime / 2.0f);
        scrollXPx += flingOffsetPx;

        if (elapsedTime > flingDurationMs) {
            scrollXPx = state->flingEndPos;
            done = 1;
        }
    } else {
        done = 1;
    }

    // Clamp
    if (scrollXPx > 0) {
        scrollXPx = 0;
    }
    if (scrollXPx < maxScrollXPx) {
        scrollXPx = maxScrollXPx;
    }

    state->currentScrollX = scrollXPx;
    if (done) {
        state->scrollX = scrollXPx;
        state->flingVelocityX = 0;
        deceleration = 0.f;
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

    print_frame_rate();

    return !done;
}

