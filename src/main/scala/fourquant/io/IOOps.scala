package fourquant.io

import fourquant.io.ScifioOps._
import io.scif.img.{ImgOpener, SCIFIOImgPlus}
import net.imglib2.`type`.NativeType
import net.imglib2.`type`.numeric.RealType
import net.imglib2.`type`.numeric.real.FloatType
import net.imglib2.img.ImgFactory
import net.imglib2.img.array.{ArrayImg, ArrayImgFactory}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

//import net.imglib2.`type`.numeric.real.FloatType
//import net.imglib2.`type`.numeric.integer.IntType

import scala.collection.JavaConversions._

/**
 * A general set of opertions for importing images
 * Created by mader on 2/27/15.
 */
object IOOps {
  implicit class fqContext(sc: SparkContext) {

    private def staticTypeReadImages[T<: RealType[T]](file: String,iFactory: ImgFactory[T],
                                                      iType: T):
    RDD[(String,SCIFIOImgPlus[T])] = {
      sc.binaryFiles(file).mapPartitions{
        curPart =>
          val io = new ImgOpener()
          curPart.flatMap{
            case (filename,pds) =>
              for (img<-io.openPDS[T](filename,pds,iFactory,iType))
              yield (filename,img)
          }
      }
    }

    def genericImages[T,U <: NativeType[U] with RealType[U]](file: String,
                                                             bType: () => U)(implicit
                                                                             tm: ClassTag[T]):
    RDD[(String, SparkImage[T,U])] = {
      class GenericSparkImage(var cs: Either[ArrayWithDim[T],ArrayImg[U,_]]) extends
        SparkImage[T,U](cs) {
        def this() = this(Left(ArrayWithDim.empty[T]))
        override val baseType: U = bType()
      }
      sc.binaryFiles(file).mapPartitions{
        curPart =>
          val io = new ImgOpener()
          curPart.flatMap{
            case (filename,pds) =>
              for (img<-io.openPDS[U](filename,pds,new ArrayImgFactory[U], bType() ))
              yield (filename,
                new SparkImage[T,U](Right(img.getImg.asInstanceOf[ArrayImg[U,_]])) {
                  def this() = this(Left())
                  override val baseType: U = bType()
                }
                )
          }
      }
    }

    def floatImages(file: String): RDD[(String,SparkFloatImg)] = {
      sc.binaryFiles(file).mapPartitions{
        curPart =>
          val io = new ImgOpener()
          curPart.flatMap{
            case (filename,pds) =>
              for (img<-io.openPDS[FloatType](filename,pds,new ArrayImgFactory[FloatType],
                new FloatType))
              yield (filename,new SparkFloatImg(img.getImg.asInstanceOf[ArrayImg[FloatType,_]]))
          }
      }
    }


  }

}
