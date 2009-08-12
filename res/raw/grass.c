// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma version(1)
#pragma stateVertex(PVBackground)
#pragma stateFragment(PFBackground)
#pragma stateFragmentStore(PFSBackground)

#define RSID_STATE 0
#define RSID_FRAME_COUNT 0
#define RSID_BLADES_COUNT 1
#define RSID_WIDTH 2
#define RSID_HEIGHT 3

#define RSID_BLADES 1
#define BLADE_STRUCT_FIELDS_COUNT 12
#define BLADE_STRUCT_ANGLE 0
#define BLADE_STRUCT_SIZE 1
#define BLADE_STRUCT_XPOS 2
#define BLADE_STRUCT_YPOS 3
#define BLADE_STRUCT_OFFSET 4
#define BLADE_STRUCT_SCALE 5
#define BLADE_STRUCT_LENGTHX 6
#define BLADE_STRUCT_LENGTHY 7
#define BLADE_STRUCT_HARDNESS 8
#define BLADE_STRUCT_H 9
#define BLADE_STRUCT_S 10
#define BLADE_STRUCT_B 11

#define TESSELATION 2.0f

#define MAX_BEND 0.09f

#define MIDNIGHT 0.0f
#define MORNING 0.375f
#define AFTERNOON 0.6f
#define DUSK 0.8f

#define SECONDS_IN_DAY 24.0f * 3600.0f

#define PI 3.1415926f

#define REAL_TIME 0

float time(int frameCount) {
    if (REAL_TIME) {
        return (hour() * 3600.0f + minute() * 60.0f + second()) / SECONDS_IN_DAY;
    }
    return (frameCount % 180) / 180.0f;
}

void alpha(float a) {
    color(1.0f, 1.0f, 1.0f, a);
}

void drawNight(int width, int height) {
    bindProgramFragment(NAMED_PFNight);
    bindTexture(NAMED_PFNight, 0, NAMED_TNight);
    drawQuadTexCoords(
            0.0f, -32.0f, 0.0f,
            0.0f, 1.0f,
            width, -32.0f, 0.0f,
            2.0f, 1.0f,
            width, 1024.0f - 32.0f, 0.0f,
            2.0f, 0.0f,
            0.0f, 1024.0f - 32.0f, 0.0f,
            0.0f, 0.0f);
    bindProgramFragment(NAMED_PFBackground);
}

void drawSunrise(int width, int height) {
    bindTexture(NAMED_PFBackground, 0, NAMED_TSunrise);
    drawRect(0.0f, 0.0f, width, height, 0.0f);
}

void drawNoon(int width, int height) {
    bindTexture(NAMED_PFBackground, 0, NAMED_TSky);
    drawRect(0.0f, 0.0f, width, height, 0.0f);
}

void drawSunset(int width, int height) {
    bindTexture(NAMED_PFBackground, 0, NAMED_TSunset);
    drawRect(0.0f, 0.0f, width, height, 0.0f);
}

void drawBlade(int index, float now, int frameCount) {
    float *bladeStruct = loadArrayF(RSID_BLADES, index);
    float offset = bladeStruct[BLADE_STRUCT_OFFSET];
    float scale = bladeStruct[BLADE_STRUCT_SCALE];
    float angle = bladeStruct[BLADE_STRUCT_ANGLE];
    float hardness = bladeStruct[BLADE_STRUCT_HARDNESS];
    
    float xpos = bladeStruct[BLADE_STRUCT_XPOS];
    float ypos = bladeStruct[BLADE_STRUCT_YPOS];

    float lengthX = bladeStruct[BLADE_STRUCT_LENGTHX];
    float lengthY = bladeStruct[BLADE_STRUCT_LENGTHY];

    int size = bladeStruct[BLADE_STRUCT_SIZE];

    float h = bladeStruct[BLADE_STRUCT_H];
    float s = bladeStruct[BLADE_STRUCT_S];
    float b = bladeStruct[BLADE_STRUCT_B];

    float newB = 1.0f;
    if (now >= MIDNIGHT && now < MORNING) {
        newB = now / MORNING;
    }

    if (now >= AFTERNOON && now < DUSK) {
        newB = 1.0f - normf(AFTERNOON, DUSK, now);
    }

    if (now >= DUSK) {
        newB = 0.0f;
    }

    hsb(h, s, lerpf(0, b, newB), 1.0f);

    float newAngle = turbulencef2(xpos * 0.006f, frameCount * 0.006f, 4.0f) - 0.5f;
    newAngle /= 2.0f;
    angle = clampf(angle + (newAngle + offset - angle) * 0.15f, -MAX_BEND, MAX_BEND);

    float currentAngle = PI / 2.0f;

    float bottomX = xpos;
    float bottomY = ypos;

    int i = size * TESSELATION;
    float lx = lengthX / TESSELATION;
    float ly = lengthY / TESSELATION;
    float ss = 4.0f / i + scale / TESSELATION;
    float sh = 0.5f / TESSELATION;
    float d = angle * hardness / TESSELATION;

    for ( ; i > 0; i--) {
        float topX = bottomX - cosf(currentAngle) * size * lx;
        float topY = bottomY - sinf(currentAngle) * size * ly;
        currentAngle += d;

        float spi = (i - 1) * ss;
        float si = i * ss;

        drawQuad(topX + spi, topY, 0.0f,
                 topX - spi, topY, 0.0f,
                 bottomX - si, bottomY + sh, 0.0f,
                 bottomX + si, bottomY + sh, 0.0f);

        bottomX = topX;
        bottomY = topY;
    }

    storeF(RSID_BLADES, index + BLADE_STRUCT_ANGLE, angle);
}

void drawBlades(float now, int frameCount) {
    // For anti-aliasing
    bindTexture(NAMED_PFBackground, 0, NAMED_TAa);

    int bladesCount = loadI32(RSID_STATE, RSID_BLADES_COUNT);
    int count = bladesCount * BLADE_STRUCT_FIELDS_COUNT;

    int i = 0;
    for ( ; i < count; i += BLADE_STRUCT_FIELDS_COUNT) {
        drawBlade(i, now, frameCount);
    }
}

int main(int launchID) {
    int width = loadI32(RSID_STATE, RSID_WIDTH);
    int height = loadI32(RSID_STATE, RSID_HEIGHT);

    int frameCount = loadI32(RSID_STATE, RSID_FRAME_COUNT);
    float now = time(frameCount);
    alpha(1.0f);

    if (now >= MIDNIGHT && now < MORNING) {
        drawNight(width, height);
        alpha(normf(MIDNIGHT, MORNING, now));
        drawSunrise(width, height);
    } else if (now >= MORNING && now < AFTERNOON) {
        drawSunrise(width, height);
        alpha(normf(MORNING, AFTERNOON, now));
        drawNoon(width, height);
    } else if (now >= AFTERNOON && now < DUSK) {
        drawNoon(width, height);
        alpha(normf(AFTERNOON, DUSK, now));
        drawSunset(width, height);
    } else if (now >= DUSK) {
        drawNight(width, height);
        alpha(1.0f - normf(DUSK, 1.0f, now));
        drawSunset(width, height);
    }

    drawBlades(now, frameCount);

    frameCount++;
    storeI32(RSID_STATE, RSID_FRAME_COUNT, frameCount);

    return 1;
}
