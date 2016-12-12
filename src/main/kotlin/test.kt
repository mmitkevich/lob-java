import java.util.*

class HeyKotlin {
    var x: Int
    var y: Long

    constructor(x: Int, y: Long) {
        this.x = x;
        this.y = y;
    }
}

fun main(args: Array<String>) {
    var v = HeyKotlin(2, 3)
    v.x = 5;
    println("Hello, x=${v.x}");
}