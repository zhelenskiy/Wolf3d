@file:Suppress("NOTHING_TO_INLINE")

package long_pack.rendering

import AbstractGraphics
import OffsetInSeconds
import MicrobenchmarkRotations
import asAbstract
import screenHeight
import screenWidth
import worldMap
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.*
import java.text.DecimalFormat
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

operator fun Array<IntArray>.get(location: LocationF): Int = this[location.x.toInt()][location.y.toInt()]

fun canMove(point: Point2f): Boolean =
    worldMap[point.x.toInt()][point.y.toInt()] == 0

private inline fun Point2f.encodeToLong() = x.toRawBits().toLong().shl(32) or y.toRawBits().toLong().and(0xFFFFFFFF)
private inline fun Vector2f.encodeToLong() = x.toRawBits().toLong().shl(32) or y.toRawBits().toLong().and(0xFFFFFFFF)
private inline fun LocationF.encodeToLong() = x.toLong().shl(32) or y.toLong().and(0xFFFFFFFF)
private inline fun Long.decodeToPoint() = Point2f(Float.fromBits(shr(32).toInt()), Float.fromBits(toInt()))
private inline fun Long.decodeToVector() = Vector2f(Float.fromBits(shr(32).toInt()), Float.fromBits(toInt()))
private inline fun Long.decodeToLocation() = LocationF(shr(32).toInt(), toInt())

@JvmInline
value class Point2f(val x: Float, val y: Float) {
    operator fun plus(vector: Vector2f): Long =
        Point2f(x + vector.x, y + vector.y).encodeToLong()

    operator fun minus(vector: Vector2f): Long =
        Point2f(x - vector.x, y - vector.y).encodeToLong()

    fun toLocation(): Long =
        LocationF(x.toInt(), y.toInt()).encodeToLong()

    fun toVector(): Long =
        Vector2f(x, y).encodeToLong()
}

@JvmInline
value class Vector2f(val x: Float, val y: Float) {
    fun rotate(angle: Float): Long =
        Vector2f(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle)).encodeToLong()

    operator fun times(factor: Float): Long =
        Vector2f(x * factor, y * factor).encodeToLong()

    operator fun plus(vector: Vector2f): Long =
        Vector2f(x + vector.x, y + vector.y).encodeToLong()

    operator fun minus(vector: Vector2f): Long =
        Vector2f(x - vector.x, y - vector.y).encodeToLong()

    fun abs(): Long =
        Vector2f(abs(x), abs(y)).encodeToLong()

    fun xProjection(): Long =
        Vector2f(x, 0.0f).encodeToLong()

    fun yProjection(): Long =
        Vector2f(0.0f, y).encodeToLong()
}

@JvmInline
value class LocationF(val x: Int, val y: Int) {
    fun toVector(): Long =
        Vector2f(x.toFloat(), y.toFloat()).encodeToLong()

    fun step(vector: Vector2f): Long =
        LocationF((toVector().decodeToVector() + vector).decodeToVector().x.toInt(), (toVector().decodeToVector() + vector).decodeToVector().y.toInt()).encodeToLong()
}

operator fun Float.div(vector: Vector2f): Long =
    Vector2f(this / vector.x, this / vector.y).encodeToLong()

class MyPanelF : JPanel(), KeyListener, MouseListener {
    private var startTime: Long = System.nanoTime()
    private var frameCount = 0
    private var fps = 100.0
    private var minFps = 100.0
    private val veryStartTime = System.nanoTime()

    var pos = Point2f(22.0f, 12.0f) // start position

    var dir = Vector2f(-1.0f, 0.0f) // direction vector

    var plane = Vector2f(0.0f, 0.66f) //the 2d raycaster version of camera plane
    init {
        addKeyListener(this)
        addMouseListener(this)
        isFocusable = true

        // Set up the timer to update the FPS every second
        Timer(1000) {
            // Calculate the FPS and update the label text
            val currentTime = System.nanoTime()
            val elapsedTime = (currentTime - startTime) / 1e9
            fps = frameCount / elapsedTime
            if ((currentTime - veryStartTime) / 1e9 > OffsetInSeconds) {
                minFps = minFps.coerceAtMost(fps)
            }
            startTime = currentTime
            frameCount = 0
        }.start()
        startTime = System.nanoTime()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        g as Graphics2D

        mainLoop(g.asAbstract())

        g.color = Color.BLACK
        g.drawString("Current FPS: ${DecimalFormat("#.#").format(fps)}, Min FPS: ${DecimalFormat("#.#").format(minFps)}", 10, 20)

        frameCount++
    }

    private fun mainLoop(g: AbstractGraphics) {
        repeat(100) {
            drawScene(pos, dir, plane, g)
        }
    }

    override fun keyPressed(e: KeyEvent) {
        val frameTime = 1 / fps.toFloat()
        //speed modifiers

        //speed modifiers
        val moveSpeed = frameTime * .5f //the constant value is in squares/second
        val rotSpeed = frameTime * .3f //the constant value is in radians/second
        when (e.keyCode) {
            KeyEvent.VK_UP -> {
                if(canMove((pos + (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint())) {
                    pos = (pos + (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint()
                }
                if(canMove((pos + (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint())) {
                    pos = (pos + (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint()
                }
            }
            KeyEvent.VK_DOWN -> {
                if(canMove((pos - (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint())) {
                    pos = (pos - (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint()
                }
                if(canMove((pos - (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint())) {
                    pos = (pos - (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint()
                }
            }
            KeyEvent.VK_LEFT -> {
                //both camera direction and camera plane must be rotated
                dir = dir.rotate(rotSpeed).decodeToVector()
                plane = plane.rotate(rotSpeed).decodeToVector()
            }
            KeyEvent.VK_RIGHT -> {
                //both camera direction and camera plane must be rotated
                dir = dir.rotate(-rotSpeed).decodeToVector()
                plane = plane.rotate(-rotSpeed).decodeToVector()
            }
        }
        repaint()
    }

    override fun mouseClicked(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}
    override fun mousePressed(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun keyTyped(e: KeyEvent) {}
    override fun keyReleased(e: KeyEvent) {}
}

fun heavyActionFloat(graphics: AbstractGraphics) {
    var pos = Point2f(22.0f, 12.0f)
    var dir = Vector2f(-1.0f, 0.0f)
    var plane = Vector2f(0.0f, 0.66f)
    val fps = 10.0f
    val frameTime = 1 / fps
    //speed modifiers

    //speed modifiers
    val moveSpeed = frameTime * .5f //the constant value is in squares/second
    val rotSpeed = frameTime * .3f //the constant value is in radians/second
    repeat(MicrobenchmarkRotations) {
        drawScene(pos, dir, plane, graphics)
        if(canMove((pos + (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint())) {
            pos = (pos + (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint()
        }
        if(canMove((pos + (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint())) {
            pos = (pos + (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint()
        }
        drawScene(pos, dir, plane, graphics)
        if(canMove((pos - (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint())) {
            pos = (pos - (dir * moveSpeed).decodeToVector().xProjection().decodeToVector()).decodeToPoint()
        }
        if(canMove((pos - (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint())) {
            pos = (pos - (dir * moveSpeed).decodeToVector().yProjection().decodeToVector()).decodeToPoint()
        }
        drawScene(pos, dir, plane, graphics)
        dir = dir.rotate(rotSpeed).decodeToVector()
        plane = plane.rotate(rotSpeed).decodeToVector()
        drawScene(pos, dir, plane, graphics)
    }
}

private fun drawScene(pos: Point2f, dir: Vector2f, plane: Vector2f, g: AbstractGraphics) {
    for (x in 0 until screenWidth) {
        //calculate ray position and direction
        val cameraX = 2 * x / screenHeight.toFloat() - 1 //x-coordinate in camera space
        val rayDir = (dir + (plane * cameraX).decodeToVector()).decodeToVector()
        //which box of the map we're in
        var mapLocation = pos.toLocation().decodeToLocation()

        //length of ray from current position to next x or y-side
        var sideDist: Vector2f

        //length of ray from one x or y-side to next x or y-side
        //these are derived as:
        //deltaDistX = sqrt(1 + (rayDirY * rayDirY) / (rayDirX * rayDirX))
        //deltaDistY = sqrt(1 + (rayDirX * rayDirX) / (rayDirY * rayDirY))
        //which can be simplified to abs(|rayDir| / rayDirX) and abs(|rayDir| / rayDirY)
        //where |rayDir| is the length of the vector (rayDirX, rayDirY). Its length,
        //unlike (dirX, dirY) is not 1, however this does not matter, only the
        //ratio between deltaDistX and deltaDistY matters, due to the way the DDA
        //stepping further below works. So the values can be computed as below.
        // Division through zero is prevented, even though technically that's not
        // needed in C++ with IEEE 754 floating point values.
        val deltaDist = (1.0f / rayDir.abs().decodeToVector()).decodeToVector()

        var perpWallDist: Float

        //what direction to step in x or y-direction (either +1 or -1)
        var step: Vector2f

        var hit = 0 //was there a wall hit?
        var side = 0 //was a NS or a EW wall hit?
        //calculate step and initial sideDist
        if (rayDir.x < 0) {
            step = Vector2f((-1).toFloat(), 0.0f)
            sideDist = ((pos.toVector().decodeToVector() - mapLocation.toVector().decodeToVector()).decodeToVector().xProjection().decodeToVector() * deltaDist.x).decodeToVector()
        } else {
            step = Vector2f(1.toFloat(), 0.0f)
            sideDist = (((mapLocation.toVector().decodeToVector() + Vector2f(1.0f, 0.0f)).decodeToVector() - pos.toVector().decodeToVector()).decodeToVector().xProjection().decodeToVector() * deltaDist.x).decodeToVector()
        }
        if (rayDir.y < 0) {
            step = (step + Vector2f(0.0f, (-1).toFloat())).decodeToVector()
            sideDist = (sideDist + ((pos.toVector().decodeToVector() - mapLocation.toVector().decodeToVector()).decodeToVector().yProjection().decodeToVector() * deltaDist.y).decodeToVector()).decodeToVector()
        } else {
            step = (step + Vector2f(0.0f, 1.toFloat())).decodeToVector()
            sideDist = (sideDist + (((mapLocation.toVector().decodeToVector() + Vector2f(0.0f, 1.0f)).decodeToVector() - pos.toVector().decodeToVector()).decodeToVector().yProjection().decodeToVector() * deltaDist.y).decodeToVector()).decodeToVector()
        }
        //perform DDA
        while (hit == 0) {
            //jump to next map square, either in x-direction, or in y-direction
            if (sideDist.x < sideDist.y) {
                sideDist = (sideDist + deltaDist.xProjection().decodeToVector()).decodeToVector()
                mapLocation = mapLocation.step(step.xProjection().decodeToVector()).decodeToLocation()
                side = 0
            } else {
                sideDist = (sideDist + deltaDist.yProjection().decodeToVector()).decodeToVector()
                mapLocation = mapLocation.step(step.yProjection().decodeToVector()).decodeToLocation()
                side = 1
            }
            //Check if ray has hit a wall
            if (worldMap[mapLocation] > 0) hit = 1
        }
        //Calculate distance projected on camera direction. This is the shortest distance from the point where the wall is
        //hit to the camera plane. Euclidean to center camera point would give fisheye effect!
        //This can be computed as (mapX - posX + (1 - stepX) / 2) / rayDirX for side == 0, or same formula with Y
        //for size == 1, but can be simplified to the code below thanks to how sideDist and deltaDist are computed:
        //because they were left scaled to |rayDir|. sideDist is the entire length of the ray above after the multiple
        //steps, but we subtract deltaDist once because one step more into the wall was taken above.
        perpWallDist = if (side == 0) (sideDist - deltaDist).decodeToVector().x else (sideDist - deltaDist).decodeToVector().y

        //Calculate height of line to draw on screen
        val lineHeight = (screenHeight / perpWallDist).toInt()

        //calculate lowest and highest pixel to fill in current stripe
        val drawStart = (-lineHeight / 2 + screenHeight / 2).coerceAtLeast(0)
        val drawEnd = (lineHeight / 2 + screenHeight / 2).coerceAtMost(screenHeight - 1)

        //choose wall color
        var color =
            when (worldMap[mapLocation]) {
                1 -> 0xFF0000 //red
                2 -> 0x00FF00 //green
                3 -> 0x00FF00 //blue
                4 -> 0x0000FF //white
                else -> 0xFFFF00 //yellow
            }

        //give x and y sides different brightness
        if (side == 1) {
            color /= 2
        }

        //draw the pixels of the stripe as a vertical line
        g.setIntColor(color)
        g.drawLine(x, drawStart, x, drawEnd)
    }
}

class MyFrameF : JFrame() {
    init {
        add(MyPanelF())
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(screenWidth, screenHeight)
        isVisible = true

        // Set up the game loop
        Thread {
            while (true) {
                // Repaint the panel and sleep for a short time
                SwingUtilities.invokeLater {
                    contentPane.repaint()
                }
                Thread.sleep(10)
            }
        }.start()
    }
}

fun main() {
    MyFrameF()
}