import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.reflect.KProperty

/**
 * Created by erichenderson on 4/3/16.
 */

fun Int.loop(proc: (Int) -> Unit) {
    for (i in 0..this - 1) {
        proc(i)
    }
}

class Model(val name: String) {
    protected val mappings: HashMap<Char, String> = HashMap()
    protected val finalMappings: HashMap<Char, String> = HashMap()
    var defaultAngleDiff: Double = 0.0
    var segments: Double = 0.0
    var iterations: Int = 4
    var initialAngle: Double = 90.0
    var initialValue: String = "F"
    var initialX: Double = 0.5
    var initialY: Double = 1.0

    fun mapping(from: Char, to: String) {
        this.mappings[from] = to
    }

    fun finalMapping(from: Char, to: String) {
        this.finalMappings[from] = to
    }

    fun String.permuteByMapping(mappings: Map<Char, String>): String {
        val sb = StringBuilder()
        for (c in this) {
            if (mappings.contains(c)) sb.append(mappings[c])
            else sb.append(c)
        }
        return sb.toString()
    }

    fun genPattern(): String {
        var pattern = initialValue
        iterations.loop { pattern = pattern.permuteByMapping(this.mappings) }
        if (finalMappings.isNotEmpty()) {
            pattern = pattern.permuteByMapping(this.finalMappings)
        }
        return pattern
    }
}

class ModelBuilder(val model: Model) {
    fun mapping(from: Char, to: String) {
        model.mapping(from, to)
    }

    fun finalMapping(from: Char, to: String) {
        model.finalMapping(from, to)
    }

    fun defaultAngleDiff(defaultAngleDiff: Double) {
        model.defaultAngleDiff = defaultAngleDiff
    }

    fun segments(segments: Double) {
        model.segments = segments
    }

    fun iterations(iterations: Int) {
        model.iterations = iterations
    }

    fun initialAngle(initialAngle: Double) {
        model.initialAngle = initialAngle
    }

    fun initialValue(initialValue: String) {
        model.initialValue = initialValue
    }

    fun initialPosition(initialX: Double, initialY: Double) {
        model.initialX = initialX
        model.initialY = initialY
    }
}

fun model(name: String, init: ModelBuilder.() -> Unit): Model {
    val model = Model(name)
    ModelBuilder(model).init()
    return model
}

class RestrictedRangeDelegate(val min: Double, val max: Double, initialValue: Double = 0.0, val loop: Boolean = true) {
    private var value: Double = fitToRange(initialValue)
    private fun fitToRange(v: Double): Double {
        if (loop) return ((v - min) % (max - min)) + min
        if (v < min) return min
        if (v > max) return max
        return v
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Double {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
        this.value = fitToRange(value)
    }
}

internal class StackData(var pos: Point2D.Double, initialAngle: Double) {
    var angle: Double by RestrictedRangeDelegate(-180.0, 180.0, initialAngle)

    fun rotate(angle: Double) {
        this.angle += angle
    }

    fun move(length: Double) {
        pos = Point2D.Double(pos.x + Math.cos(Math.toRadians(angle)) * length, pos.y - Math.sin(Math.toRadians(angle)) * length)
    }

    fun clone(): StackData = StackData(this.pos, this.angle)
}

class DrawPanel(model: Model) : JPanel() {
    var curModel: Model = model
        set(value) {
            field = value
            angleDiff = value.defaultAngleDiff
        }
    var angleDiff: Double by RestrictedRangeDelegate(5.0, 120.0, model.defaultAngleDiff, false)

    init {
        this.background = Color.WHITE
    }

    override fun paint(g: Graphics?) {
        if (g == null) return
        val pattern = curModel.genPattern()
        val g2 = g as Graphics2D
        g2.color = Color.WHITE
        g2.drawRect(0, 0, this.width, this.height)
        g2.color = Color.BLACK
        val stk = Stack<StackData>()
        var curData = StackData(Point2D.Double((this.width - 20) * curModel.initialX + 10, (this.height - 20) * curModel.initialY + 10), curModel.initialAngle)
        val lineLength = (this.height - 20) / Math.pow(curModel.segments, curModel.iterations.toDouble())
        for (c in pattern) {
            when (c) {
                'F' -> {
                    val oldPoint = curData.pos
                    curData.move(lineLength)
                    g2.draw(Line2D.Double(oldPoint, curData.pos))
                }
                'f' -> curData.move(lineLength)
                '-' -> curData.rotate(angleDiff)
                '+' -> curData.rotate(-angleDiff)
                '|' -> curData.rotate(180.0)
                '[' -> stk.push(curData.clone())
                ']' -> curData = stk.pop()
            }
        }
    }
}

class IntegerRestrictedVariableRangeDelegate(var min: Int, var max: Int, initialValue: Int = 0, val loop: Boolean = true) {
    private var value: Int = fitToRange(initialValue)
    private fun fitToRange(v: Int): Int {
        if (loop) return ((v - min) % (max - min)) + min
        if (v < min) return min
        if (v > max) return max
        return v
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        this.value = fitToRange(value)
    }
}

class MainClass : JFrame("Fractal Test") {
    val dp: DrawPanel = DrawPanel(Model("default"))
    val models: MutableList<Model> = ArrayList()
    val modelIndexDelegate = IntegerRestrictedVariableRangeDelegate(0, 0, 0, false)
    var modelIndex: Int by modelIndexDelegate
    val inc: Double = 0.5

    init {
        setupModels()
        modelIndexDelegate.max = models.size - 1
        dp.curModel = models[modelIndex]
        this.size = Dimension(750, 750)
        this.defaultCloseOperation = EXIT_ON_CLOSE
        this.background = Color.WHITE
        this.isResizable = false
        this.layout = GridLayout(1, 1)
        this.add(dp)
        this.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e == null) return
                when (e.keyCode) {
                    KeyEvent.VK_LEFT   -> dp.curModel = models[--modelIndex]
                    KeyEvent.VK_RIGHT  -> dp.curModel = models[++modelIndex]
                    KeyEvent.VK_UP     -> dp.angleDiff += inc * if (e.isControlDown) 10 else 1
                    KeyEvent.VK_DOWN   -> dp.angleDiff -= inc * if (e.isControlDown) 10 else 1
                    KeyEvent.VK_ESCAPE -> System.exit(0)
                }
                title = "Angle Diff: " + dp.angleDiff + "; Model: " + models[modelIndex].name
                repaint()
            }
        })
        this.isVisible = true
        title = "Angle Diff: " + dp.angleDiff + "; Model: " + models[modelIndex].name
    }

    private fun setupModels() {
        models.add(model("Weed") {
            defaultAngleDiff(25.0)
            segments(3.0)
            mapping('F', "F[-F]F[+F]F")
        })
        models.add(model("Weed 2") {
            iterations(6)
            defaultAngleDiff(25.0)
            segments(2.4)
            initialValue("X")
            initialAngle(60.0)
            initialPosition(0.2, 0.9)
            mapping('X', "F-[[X]+X]+F[+FX]-X")
            mapping('F', "FF")
        })
        models.add(model("Vine") {
            defaultAngleDiff(70.0)
            segments(3.0)
            mapping('F', "FF+F+F+FF+F+F-F")
        })
        models.add(model("Design 1") {
            iterations(5)
            defaultAngleDiff(90.0)
            segments(3.0)
            mapping('F', "FF[-F-F][+F+F]F")
        })
        models.add(model("Design 2") {
            iterations(5)
            defaultAngleDiff(90.0)
            segments(3.0)
            mapping('F', "F[|+F][|-F]F[-F][+F]F")
        })
        models.add(model("Design 3") {
            iterations(5)
            defaultAngleDiff(60.0)
            segments(2.75)
            initialValue("[FX][+FX][|+FX][-FX][|-FX][|FX]")
            initialPosition(0.5, 0.5)
            mapping('X', "[FX][+FX][|+FX][-FX][|-FX]")
            mapping('F', "FF")
        })
        models.add(model("Design 4") {
            iterations(7)
            defaultAngleDiff(90.0)
            segments(2.75)
            initialValue("[|Y][FXFX][+FXFXFX-FXFX][-FXFXFX+FXFX]")
            initialAngle(0.0)
            initialPosition(0.25, 0.5)
            mapping('X', "[FX][+FX][-FX]")
            mapping('Y', "[FX][+FX][-FX][|FX]")
            mapping('F', "FF")
        })
        models.add(model("Peano") {
            defaultAngleDiff(90.0)
            segments(3.0)
            initialValue("X")
            initialPosition(0.0, 1.0)
            mapping('X', "XFYFX+F+YFXFY-F-XFYFX")
            mapping('Y', "YFXFY-F-XFYFX+F+YFXFY")
        })
        models.add(model("Space-filling 2") {
            iterations(6)
            defaultAngleDiff(90.0)
            segments(2.0)
            initialValue("X")
            initialAngle(-90.0)
            initialPosition(0.0, 0.0)
            mapping('X', "-YF+XFX+FY-")
            mapping('Y', "+XF-YFY-FX+")
        })
        models.add(model("Koch") {
            defaultAngleDiff(60.0)
            segments(3.0)
            mapping('F', "F-F++F-F")
        })
        models.add(model("Koch 2") {
            defaultAngleDiff(90.0)
            segments(3.0)
            mapping('F', "F-F+F+F-F")
        })
        models.add(model("Koch") {
            defaultAngleDiff(60.0)
            segments(3.1)
            initialValue("F++F++F")
            initialAngle(120.0)
            mapping('F', "F-F++F-F")
        })
        models.add(model("Sierpinski") {
            iterations(8)
            defaultAngleDiff(60.0)
            segments(2.0)
            initialValue("A")
            initialAngle(-60.0)
            initialPosition(0.5, 0.05)
            mapping('A', "B-A-B")
            mapping('B', "A+B+A")
            finalMapping('A', "F")
            finalMapping('B', "F")
        })
        models.add(model("Carpet") {
            defaultAngleDiff(90.0)
            segments(3.0)
            mapping('F', "F+F-F-F-f+F+F+F-F")
            mapping('f', "fff")
        })
        models.add(model("Median") {
            iterations(8)
            defaultAngleDiff(45.0)
            segments(1.645)
            initialValue("L--F--L--F")
            mapping('L', "+R-F-R+")
            mapping('R', "-L+F+L-")
            finalMapping('L', "F")
            finalMapping('R', "F")
        })
        models.add(model("Dragon") {
            iterations(10)
            defaultAngleDiff(90.0)
            segments(1.5)
            initialValue("FX")
            initialPosition(0.3, 0.6)
            mapping('X', "X+YF")
            mapping('Y', "FX-Y")
        })
        models.add(model("Gosper") {
            defaultAngleDiff(60.0)
            segments(3.0)
            initialValue("XF")
            initialAngle(30.0)
            initialPosition(0.4, 0.2)
            mapping('X', "X+YF++YF-FX--FXFX-YF+")
            mapping('Y', "-FX+YFYF++YF+FX--FX-Y")
        })
        models.add(model("Penrose") {
            iterations(4)
            defaultAngleDiff(36.0)
            segments(2.0)
            initialValue("[7]++[7]++[7]++[7]++[7]")
            initialPosition(0.5, 0.5)
            mapping('6', "81++91----71[-81----61]++")
            mapping('7', "+81--91[---61--71]+")
            mapping('8', "-61++71[+++81++91]-")
            mapping('9', "--81++++61[+91++++71]--71")
            mapping('1', "")
            finalMapping('1', "F")
        })
        models.add(model("Pleasant Error") {
            defaultAngleDiff(72.0)
            segments(3.05)
            initialValue("F-F-F-F-F")
            initialAngle(18.0)
            initialPosition(0.375, 0.075)
            mapping('F', "F-F++F+F-F-F")
        })
        models.add(model("Lace") {
            iterations(6)
            defaultAngleDiff(30.0)
            segments(2.11)
            initialValue("W")
            initialPosition(0.0, 1.0)
            mapping('W', "+++X--F--ZFX+")
            mapping('X', "---W++F++YFW-")
            mapping('Y', "+ZFX--F--Z+++")
            mapping('Z', "-YFW++F++Y---")
            finalMapping('W', "F")
            finalMapping('X', "F")
            finalMapping('Y', "F")
            finalMapping('Z', "F")
        })
    }
}

fun main(args: Array<String>) {
    MainClass()
}