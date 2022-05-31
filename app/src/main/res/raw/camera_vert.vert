// 把顶点坐标给这个变量， 确定要画画的形状
//字节定义的  4个   数组  矩阵
attribute vec4 vPosition;//0
//cpu
//接收纹理坐标，接收采样器采样图片的坐标  camera
attribute vec4 vCoord;

//   oepngl    camera
uniform mat4 vMatrix;

//传给片元着色器 像素点
varying vec2 aCoord;
void main(){
//    gpu  需要渲染的 什么图像   形状
    gl_Position=vPosition;
//    遍历的   for循环   性能比较低
    //通过varying修饰的变量会同步传值给片元着色器的同名变量
aCoord= (vMatrix * vCoord).xy;
}
