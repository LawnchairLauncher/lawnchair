#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PF)
#pragma stateFragmentStore(PFS)

#define PI 3.14159f


// Attraction to center values from page edge to page center.
float g_AttractionTable[9];
float g_FrictionTable[9];
float g_PhysicsTableSize;

float g_PosPage;
float g_PosVelocity;
float g_LastPositionX;
int g_LastTouchDown;
float g_DT;
int g_LastTime;
int g_Rendering;
int g_PageCount;

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

void init() {
    g_AttractionTable[0] = 4.5f;
    g_AttractionTable[1] = 4.5f;
    g_AttractionTable[2] = 5.0f;
    g_AttractionTable[3] = 4.0f;
    g_AttractionTable[4] = -4.0f;
    g_AttractionTable[5] = -5.0f;
    g_AttractionTable[6] = -4.5f;
    g_AttractionTable[7] = -4.5f;
    g_AttractionTable[8] = -4.5f;  // dup 7 to avoid a clamp later
    g_FrictionTable[0] = 3.5f;
    g_FrictionTable[1] = 3.6f;
    g_FrictionTable[2] = 3.7f;
    g_FrictionTable[3] = 3.8f;
    g_FrictionTable[4] = 3.8f;
    g_FrictionTable[5] = 3.7f;
    g_FrictionTable[6] = 3.6f;
    g_FrictionTable[7] = 3.5f;
    g_FrictionTable[8] = 3.5f;  // dup 7 to avoid a clamp later
    g_PhysicsTableSize = 7;

    g_PosVelocity = 0;
    g_PosPage = 0;
    g_LastTouchDown = 0;
    g_LastPositionX = 0;
}

void clampPosition() {
    if (g_PosPage < 0) {
        g_PosPage = 0;
        g_PosVelocity = 0;
    }
    if (g_PosPage > (g_PageCount - 1)) {
        g_PosPage = (g_PageCount - 1);
        g_PosVelocity = 0;
    }
}

void move() {
    if (g_LastTouchDown) {
        float dx = -(state->newPositionX - g_LastPositionX);
        g_PosVelocity = 0;
        g_PosPage += dx;
    }
    g_LastTouchDown = state->newTouchDown;
    g_LastPositionX = state->newPositionX;
    clampPosition();
}

void fling() {
    g_LastTouchDown = 0;
    g_PosVelocity = -state->flingVelocityX;
    if (g_PosPage <= 0) {
        g_PosVelocity = maxf(0, g_PosVelocity);
    }
    if (g_PosPage > (g_PageCount - 1)) {
        g_PosVelocity = minf(0, (g_PageCount - 1) - g_PosPage);
    }
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

void updatePos() {
    if (g_LastTouchDown) {
        return;
    }

    //debugF("g_PosPage", g_PosPage);
    //debugF("  g_PosVelocity", g_PosVelocity);

    float tablePosNorm = fracf(g_PosPage + 0.5f);
    float tablePosF = tablePosNorm * g_PhysicsTableSize;
    int tablePosI = tablePosF;
    float tablePosFrac = tablePosF - tablePosI;
    //debugF("tablePosNorm", tablePosNorm);
    //debugF("tablePosF", tablePosF);
    //debugF("tablePosI", tablePosI);
    //debugF("tablePosFrac", tablePosFrac);

    float accel = lerpf(g_AttractionTable[tablePosI],
                        g_AttractionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;
    float friction = lerpf(g_FrictionTable[tablePosI],
                           g_FrictionTable[tablePosI + 1],
                           tablePosFrac) * g_DT;
    //debugF("  accel", accel);
    //debugF("  friction", friction);

    g_PosVelocity += accel;
    if ((friction > fabsf(g_PosVelocity)) && (friction > fabsf(accel))) {
        // Special get back to center and overcome friction physics.
        float t = tablePosNorm - 0.5f;
        if (fabsf(t) < (friction * g_DT)) {
            // really close, just snap
            g_PosPage = roundf(g_PosPage);
            g_PosVelocity = 0;
        } else {
            if (t > 0) {
                g_PosVelocity = -friction;
            } else {
                g_PosVelocity = friction;
            }
        }
    } else {
        // Normal physics
        if (g_PosVelocity > 0) {
            g_PosVelocity -= friction;
            if (g_PosVelocity < 0) {
                g_PosVelocity = 0;
            }
        } else {
            g_PosVelocity += friction;
            if (g_PosVelocity > 0) {
                g_PosVelocity = 0;
            }
        }
    }
    g_PosPage += g_PosVelocity * g_DT;

    // Check for out of boundry conditions.
    if (g_PosPage < 0 && g_PosVelocity < 0) {
        float damp = 1.0 + (g_PosPage * 3);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
    if (g_PosPage > (g_PageCount-1) && g_PosVelocity > 0) {
        float damp = 1.0 - ((g_PosPage - g_PageCount + 1) * 3);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
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

    //debugF("center angle", centerAngle);
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

    int newTime = uptimeMillis();
    g_DT = (newTime - g_LastTime) / 1000.f;
    g_LastTime = newTime;
    //debugF("*** dt ", g_DT);

    // If we're not supposed to be showing, don't do anything.
    if (!state->visible) {
        return 0;
    }

    // icons & labels
    int iconCount = state->iconCount;
    g_PageCount = count_pages(iconCount);

    updatePos(0.1f);
    state->readPosX = g_PosPage;
    state->readVel = g_PosVelocity;

    //debugF("    draw g_PosPage", g_PosPage);

    // Draw the icons ========================================
    bindProgramVertex(NAMED_PV);
    bindProgramFragment(NAMED_PF);
    bindProgramFragmentStore(NAMED_PFS);

    // Bug makes 1.0f alpha fail.
    color(1.0f, 1.0f, 1.0f, 0.99f);

    int lastIcon = iconCount-1;

    int page = g_PosPage;
    float currentPagePosition = g_PosPage - page;

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

    //print_frame_rate();

    // Bug workaround where the last frame is not always displayed
    // So we render the last frame twice.
    int rendering = g_Rendering;
    g_Rendering = (g_PosVelocity != 0) || fracf(g_PosPage);
    rendering |= g_Rendering;
    return rendering;
}

