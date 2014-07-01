# This script is used to push the most up-to-date files from
# Launcher into frameworks' version of the WallpaperCropActivity
# (and supporting files)
# The framework versions have some small modifications that are
# necessary so do this with care
import os
import sys
src_dir = "WallpaperPicker/src/"
files = """
src/android/util/Pools.java
com/android/gallery3d/util/IntArray.java
com/android/gallery3d/common/Utils.java
com/android/gallery3d/exif/ByteBufferInputStream.java
com/android/gallery3d/exif/CountedDataInputStream.java
com/android/gallery3d/exif/ExifData.java
com/android/gallery3d/exif/ExifInterface.java
com/android/gallery3d/exif/ExifInvalidFormatException.java
com/android/gallery3d/exif/ExifModifier.java
com/android/gallery3d/exif/ExifOutputStream.java
com/android/gallery3d/exif/ExifParser.java
com/android/gallery3d/exif/ExifReader.java
com/android/gallery3d/exif/ExifTag.java
com/android/gallery3d/exif/IfdData.java
com/android/gallery3d/exif/IfdId.java
com/android/gallery3d/exif/JpegHeader.java
com/android/gallery3d/exif/OrderedDataOutputStream.java
com/android/gallery3d/exif/Rational.java
com/android/gallery3d/glrenderer/BasicTexture.java
com/android/gallery3d/glrenderer/BitmapTexture.java
com/android/gallery3d/glrenderer/GLCanvas.java
com/android/gallery3d/glrenderer/GLES20Canvas.java
com/android/gallery3d/glrenderer/GLES20IdImpl.java
com/android/gallery3d/glrenderer/GLId.java
com/android/gallery3d/glrenderer/GLPaint.java
com/android/gallery3d/glrenderer/RawTexture.java
com/android/gallery3d/glrenderer/Texture.java
com/android/gallery3d/glrenderer/UploadedTexture.java
com/android/photos/BitmapRegionTileSource.java
com/android/photos/views/BlockingGLTextureView.java
com/android/photos/views/TiledImageRenderer.java
com/android/photos/views/TiledImageView.java
com/android/gallery3d/common/BitmapUtils.java
com/android/launcher3/CropView.java
com/android/launcher3/WallpaperCropActivity.java
"""

if len(sys.argv) != 2:
    print "Usage: python update_sytem_wallpaper_cropper.py <framework_dir>"
    exit()
framework_dir = sys.argv[1] + "/packages/WallpaperCropper"
for file_path in files.split():
    file_path = src_dir + file_path
    dir = os.path.dirname(file_path)
    dir = dir.replace("launcher3", "wallpapercropper")
    dir = dir.replace(src_dir, "src/")
    cmd = 'cp %s %s/%s' % (file_path, framework_dir, dir)
    print cmd
    os.system(cmd)
