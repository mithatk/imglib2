package tests;

import ij.ImageJ;

import javax.media.j3d.Transform3D;

import mpicbg.models.AffineModel3D;
import net.imglib2.algorithm.gauss.GaussianConvolution;
import net.imglib2.algorithm.transformation.ImageTransform;
import net.imglib2.image.display.imagej.ImgLib2Display;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.io.ImgOpener;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory.Boundary;
import net.imglib2.type.numeric.real.FloatType;

public class OpenAndDisplay
{	
	public static void test( ImgFactory<FloatType> factory )
	{
		ImgOpener io = new ImgOpener();
		
		
		//Img<FloatType> img = LOCI.openLOCIFloatType( "/home/saalfeld/Desktop/73.tif",  factory );
		Img<FloatType> img = io.openImg( "/home/saalfeld/Desktop/73.tif",  factory );
		
		ImgLib2Display.copyToImagePlus( img, new int[] {2, 0, 1} ).show();
		
		// compute a gaussian convolution with sigma = 3
		GaussianConvolution<FloatType> gauss = new GaussianConvolution<FloatType>( img, new OutOfBoundsMirrorFactory<FloatType, Img<FloatType>>( Boundary.DOUBLE ), 2 );
		
		if ( !gauss.checkInput() || !gauss.process() )
		{
			System.out.println( gauss.getErrorMessage() );
			return;
		}
		
		ImgLib2Display.copyToImagePlus( gauss.getResult() ).show();

		// Affine Model rotates 45 around X, 45 around Z and scales by 0.5		
		AffineModel3D model = new AffineModel3D();
		model.set( 0.35355338f, -0.35355338f, 0.0f, 0.0f, 0.25f, 0.25f, -0.35355338f, 0.0f, 0.25f, 0.25f, 0.35355338f, 0.0f );

		OutOfBoundsFactory<FloatType, Img<FloatType>> oob = new OutOfBoundsMirrorFactory<FloatType, Img<FloatType>>( Boundary.DOUBLE );
		NearestNeighborInterpolatorFactory< FloatType > interpolatorFactory = new NearestNeighborInterpolatorFactory< FloatType >( oob );
		ImageTransform< FloatType > transform = new ImageTransform<FloatType>( img, model, interpolatorFactory );
		
		if ( !transform.checkInput() || !transform.process() )
		{
			System.out.println( transform.getErrorMessage() );
			return;
		}
		
		ImgLib2Display.copyToImagePlus( transform.getResult() ).show();
		
	}
	
	public static void main( String[] args )
	{
		new ImageJ();
		
		test( new CellImgFactory<FloatType>( 64 ) );
	}
	
	public static AffineModel3D getAffineModel3D( Transform3D transform )
	{
		final float[] m = new float[16];
		transform.get( m );

		AffineModel3D model = new AffineModel3D();
		model.set( m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11] );

		return model;
	}	
}
