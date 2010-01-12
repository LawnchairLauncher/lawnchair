#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PFTexLinear)
#pragma stateStore(PSIcons)

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
int g_PageCount;
float g_Zoom;

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

float g_OldPosPage;
float g_OldPosVelocity;
float g_OldZoom;

int g_DrawLastFrame;
int lastFrame(int draw) {
    // We draw one extra frame to work around the last frame post bug.
    // We also need to track if we drew the last frame to deal with large DT
    // in the physics.
    int ret = g_DrawLastFrame | draw;
    g_DrawLastFrame = draw;
    return ret;  // should return draw instead.
}

void updateReadback() {
    if ((g_OldPosPage != g_PosPage) ||
        (g_OldPosVelocity != g_PosVelocity) ||
        (g_OldZoom != g_Zoom)) {

        g_OldPosPage = g_PosPage;
        g_OldPosVelocity = g_PosVelocity;
        g_OldZoom = g_Zoom;

        int i[3];
        i[0] = g_PosPage * (1 << 16);
        i[1] = g_PosVelocity * (1 << 16);
        i[2] = g_OldZoom * (1 << 16);
        sendToClient(&i[0], 1, 12, 1);
    }
}

void init() {
    g_AttractionTable[0] = 6.5f;
    g_AttractionTable[1] = 6.5f;
    g_AttractionTable[2] = 7.0f;
    g_AttractionTable[3] = 6.0f;
    g_AttractionTable[4] = -6.0f;
    g_AttractionTable[5] = -7.0f;
    g_AttractionTable[6] = -6.5f;
    g_AttractionTable[7] = -6.5f;
    g_AttractionTable[8] = -6.5f;  // dup 7 to avoid a clamp later
    g_FrictionTable[0] = 3.5f;
    g_FrictionTable[1] = 3.6f;
    g_FrictionTable[2] = 4.0f;
    g_FrictionTable[3] = 5.0f;
    g_FrictionTable[4] = 5.0f;
    g_FrictionTable[5] = 4.0f;
    g_FrictionTable[6] = 3.6f;
    g_FrictionTable[7] = 3.5f;
    g_FrictionTable[8] = 3.5f;  // dup 7 to avoid a clamp later
    g_PhysicsTableSize = 7;

    g_PosVelocity = 0;
    g_PosPage = 0;
    g_LastTouchDown = 0;
    g_LastPositionX = 0;
    g_Zoom = 0;
}

void resetHWWar() {
}

void move() {
    if (g_LastTouchDown) {
        float dx = -(state->newPositionX - g_LastPositionX);
        g_PosVelocity = 0;
        g_PosPage += dx;

        float pmin = -0.25f;
        float pmax = (g_PageCount - 1) + 0.25f;
        g_PosPage = clampf(g_PosPage, pmin, pmax);
    }
    g_LastTouchDown = state->newTouchDown;
    g_LastPositionX = state->newPositionX;
    //debugF("Move P", g_PosPage);
}

void fling() {
    g_LastTouchDown = 0;
    g_PosVelocity = -state->flingVelocityX;
    float av = fabsf(g_PosVelocity);
    float minVel = 3.5f;

    minVel *= 1.f - (fabsf(fracf(g_PosPage + 0.5f) - 0.5f) * 0.45f);

    if (av < minVel && av > 0.2f) {
        if (g_PosVelocity > 0) {
            g_PosVelocity = minVel;
        } else {
            g_PosVelocity = -minVel;
        }
    }

    if (g_PosPage <= 0) {
        g_PosVelocity = maxf(0, g_PosVelocity);
    }
    if (g_PosPage > (g_PageCount - 1)) {
        g_PosVelocity = minf(0, g_PosVelocity);
    }
    //debugF("fling v", g_PosVelocity);
}

void touchUp() {
    g_LastTouchDown = 0;
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

    float tablePosNorm = fracf(g_PosPage + 0.5f);
    float tablePosF = tablePosNorm * g_PhysicsTableSize;
    int tablePosI = tablePosF;
    float tablePosFrac = tablePosF - tablePosI;
    float accel = lerpf(g_AttractionTable[tablePosI],
                        g_AttractionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;
    float friction = lerpf(g_FrictionTable[tablePosI],
                           g_FrictionTable[tablePosI + 1],
                           tablePosFrac) * g_DT;
    //debugF("  accel", accel);
    //debugF("  friction", friction);

    // If our velocity is low OR acceleration is opposing it, apply it.
    if (fabsf(g_PosVelocity) < 1.0f || (g_PosVelocity * accel) < 0) {
        g_PosVelocity += accel;
    }

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
            g_PosVelocity = maxf(g_PosVelocity, 0);
        } else {
            g_PosVelocity += friction;
            g_PosVelocity = minf(g_PosVelocity, 0);
        }
    }
    g_PosPage += g_PosVelocity * g_DT;

    // Check for out of boundry conditions.
    if (g_PosPage < 0 && g_PosVelocity < 0) {
        float damp = 1.0 + (g_PosPage * 4);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
    if (g_PosPage > (g_PageCount-1) && g_PosVelocity > 0) {
        float damp = 1.0 - ((g_PosPage - g_PageCount + 1) * 4);
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
draw_page(int icon, int lastIcon, float centerAngle, float scale)
{
    int row;
    int col;

    //debugF("center angle", centerAngle);

    float iconTextureWidth = ICON_WIDTH_PX / (float)ICON_TEXTURE_WIDTH_PX;
    float iconTextureHeight = ICON_HEIGHT_PX / (float)ICON_TEXTURE_HEIGHT_PX;

    float iconWidthAngle = VIEW_ANGLE * ICON_WIDTH_PX / SCREEN_WIDTH_PX;
    float columnGutterAngle = iconWidthAngle * 0.9f;

    float farIconSize = FAR_ICON_SIZE;
    float iconGutterHeight = farIconSize * 1.3f;

    float farIconTextureSize = far_size(2 * ICON_TEXTURE_WIDTH_PX / (float)SCREEN_WIDTH_PX);

    float normalizedLabelWidth = 2 * params->bubbleWidth / (float)SCREEN_WIDTH_PX;
    float farLabelHeight = far_size(params->bubbleHeight * (normalizedLabelWidth / params->bubbleWidth));

    for (row=0; row<ROWS_PER_PAGE && icon<=lastIcon; row++) {
        float angle = centerAngle;
        angle -= (columnGutterAngle + iconWidthAngle) * 1.5f;

        float iconTop = (farIconSize + iconGutterHeight) * (1.85f + ICON_TOP_OFFSET)
                - row * (farIconSize + iconGutterHeight);
        float iconBottom = iconTop - farIconSize;

        float labelY = iconBottom - farLabelHeight;
        float iconTextureTop = iconTop + (0.5f * (farIconTextureSize - farIconSize));
        float iconTextureBottom = iconTextureTop - farIconTextureSize;

        for (col=0; col<COLUMNS_PER_PAGE && icon<=lastIcon; col++) {
            // icon
            float sine = sinf(angle);
            float cosine = cosf(angle);

            float centerX = sine * RADIUS;
            float centerZ = cosine * RADIUS / scale;

            if (scale > 1.f) {
                centerX *= scale;
            }

            float iconLeftX = centerX  - (/*cosine * */ farIconTextureSize * .5);
            float iconRightX = centerX + (/*cosine * */ farIconTextureSize * .5);
            float iconLeftZ = centerZ;// + (sine * farIconTextureSize * .5);
            float iconRightZ = centerZ;// - (sine * farIconTextureSize * .5);

            color(1.0f, 1.0f, 1.0f, 0.99f);
            if (state->selectedIconIndex == icon) {
                bindTexture(NAMED_PFTexLinear, 0, state->selectedIconTexture);
                drawQuadTexCoords(
                        iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                        iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                        iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                        iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);
            } else {
                bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_ICON_IDS, icon));
                drawQuadTexCoords(
                        iconLeftX, iconTextureTop, iconLeftZ,       0.0f, 0.0f,
                        iconRightX, iconTextureTop, iconRightZ,     1.0f, 0.0f,
                        iconRightX, iconTextureBottom, iconRightZ,  1.0f, 1.0f,
                        iconLeftX, iconTextureBottom, iconLeftZ,    0.0f, 1.0f);
            }

            // label
            if (scale < 1.2f) {
                float a = (1.2f - maxf(scale, 1.0f)) * 5;
                color(1.0f, 1.0f, 1.0f, a);
                bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_LABEL_IDS, icon));
                drawSprite(centerX, labelY, centerZ,
                           params->bubbleBitmapWidth, params->bubbleBitmapHeight);
            }

            angle += columnGutterAngle + iconWidthAngle;
            icon++;
        }
    }
}

void
draw_home_button()
{
    color(1.0f, 1.0f, 1.0f, 1.0f);
    bindTexture(NAMED_PFTexLinear, 0, state->homeButtonId);

    float scale = 2.0f / SCREEN_WIDTH_PX;

    float x = 0.0f;

    float y = -(SCREEN_HEIGHT_PX / (float)SCREEN_WIDTH_PX);
    y += g_Zoom * (scale * params->homeButtonTextureHeight / 2);

    float z = 0.0f;
    drawSprite(x, y, z, params->homeButtonTextureWidth, params->homeButtonTextureHeight);
}

int
main(int launchID)
{
    // Compute dt in seconds.
    int newTime = uptimeMillis();
    g_DT = (newTime - g_LastTime) / 1000.f;
    g_LastTime = newTime;

    if (!g_DrawLastFrame) {
        // If we stopped rendering we cannot use DT.
        // assume 30fps in this case.
        g_DT = 0.033f;
    }
    if (g_DT > 0.2f) {
        // physics may break if DT is large.
        g_DT = 0.2f;
    }

    //debugF("zoom", g_Zoom);
    if (g_Zoom != state->zoomTarget) {
        float dz = (state->zoomTarget - g_Zoom) * g_DT * 5;
        if (dz && (fabsf(dz) < 0.03f)) {
            if (dz > 0) {
                dz = 0.03f;
            } else {
                dz = -0.03f;
            }
        }
        if (fabsf(g_Zoom - state->zoomTarget) < fabsf(dz)) {
            g_Zoom = state->zoomTarget;
        } else {
            g_Zoom += dz;
        }
        updateReadback();
    }

    // Set clear value to dim the background based on the zoom position.
    if ((g_Zoom < 0.001f) && (state->zoomTarget < 0.001f)) {
        pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // When we're zoomed out and not tracking motion events, reset the pos to 0.
        if (!g_LastTouchDown) {
            g_PosPage = 0;
        }
        return lastFrame(0);
    } else if (g_Zoom < 0.85f) {
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    } else {
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    }

    // icons & labels
    int iconCount = state->iconCount;
    g_PageCount = count_pages(iconCount);

    updatePos(0.1f);
    updateReadback();

    //debugF("    draw g_PosPage", g_PosPage);

    // Draw the icons ========================================

    // Bug makes 1.0f alpha fail.
    color(1.0f, 1.0f, 1.0f, 0.99f);

    if (iconCount <= 0) {
        return lastFrame(0);
    }
    int lastIcon = iconCount-1;

    int page = g_PosPage;
    float currentPagePosition = g_PosPage - page;

    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    int icon = clamp(iconsPerPage * page, 0, lastIcon);

    float scale = (1 / g_Zoom);

    float pageAngle = VIEW_ANGLE * 1.2f;
    draw_page(icon, lastIcon, -pageAngle*currentPagePosition, scale);
    draw_page(icon+iconsPerPage, lastIcon, (-pageAngle*currentPagePosition)+pageAngle, scale);

    // Draw the border lines for debugging ========================================
    /*
    bindProgramVertex(NAMED_PVOrtho);
    bindProgramFragment(NAMED_PFOrtho);
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

    // Draw the home button ========================================
    draw_home_button();

    /*
    bindTexture(NAMED_PFOrtho, 0, loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_ID));
    float handleLeft = 40 + (320 * (scrollXPx/(float)(maxScrollXPx)));
    float handleTop = 680;
    float handleWidth = loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_TEX_WIDTH);
    float handleHeight = loadI32(ALLOC_PARAMS, PARAM_SCROLL_HANDLE_TEX_HEIGHT);
    drawRect(handleLeft, handleTop, handleLeft+handleWidth, handleTop+handleHeight, 0.0f);
    */

    // Bug workaround where the last frame is not always displayed
    // So we keep rendering until the bug is fixed.
    return lastFrame((g_PosVelocity != 0) || fracf(g_PosPage) || (g_Zoom != state->zoomTarget));
}

