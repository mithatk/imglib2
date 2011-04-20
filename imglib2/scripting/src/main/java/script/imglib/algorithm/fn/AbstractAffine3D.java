package script.imglib.algorithm.fn;

import script.imglib.color.Alpha;
import script.imglib.color.Blue;
import script.imglib.color.Green;
import script.imglib.color.RGBA;
import script.imglib.color.Red;
import script.imglib.math.Compute;
import mpicbg.imglib.algorithm.transformation.ImageTransform;
import mpicbg.imglib.img.AbstractImg;
import mpicbg.imglib.img.Img;
import mpicbg.imglib.img.ImgCursor;
import mpicbg.imglib.img.ImgRandomAccess;
import mpicbg.imglib.img.array.ArrayImg;
import mpicbg.imglib.img.basictypeaccess.FloatAccess;
import mpicbg.imglib.interpolation.InterpolatorFactory;
import mpicbg.imglib.interpolation.randomaccess.NLinearInterpolatorFactory;
import mpicbg.imglib.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsConstantValueFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsFactory;
import mpicbg.imglib.type.Type;
import mpicbg.imglib.type.numeric.NumericType;
import mpicbg.imglib.type.numeric.ARGBType;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.AffineModel2D;
import mpicbg.models.AffineModel3D;

/** Convenient intermediate class to be able to operate directly on an {@link Image} argument in the constructor. */
public abstract class AbstractAffine3D<T extends NumericType<T>> extends ImgProxy<T>
{
	static public enum Mode { LINEAR, NEAREST_NEIGHBOR };

	static public final Mode LINEAR = Mode.LINEAR;
	static public final Mode NEAREST_NEIGHBOR = Mode.NEAREST_NEIGHBOR;
	static public final Mode BEST = Mode.LINEAR;

	/** With a default {@link OutOfBoundsStrategyValueFactory} with @param outside. */
	@SuppressWarnings("unchecked")
	public AbstractAffine3D(final Img<T> img, final float[] matrix, final Mode mode, final Number outside) throws Exception {
		this(img, matrix, mode, new OutOfBoundsConstantValueFactory<T,Img<T>>((T)withValue(img, img.firstElement().createVariable(), outside))); // default value is zero
	}

	public AbstractAffine3D(final Img<T> img, final float[] matrix, final Mode mode, final OutOfBoundsFactory<T,Img<T>> oobf) throws Exception {
		super(process(img, matrix, mode, oobf));
	}

	/** With a default {@link OutOfBoundsStrategyValueFactory} with @param outside. */
	@SuppressWarnings("unchecked")
	public AbstractAffine3D(final Img<T> img,
			final float scaleX, final float shearX,
			final float shearY, final float scaleY,
			final float translateX, final float translateY,
			final Mode mode, final Number outside) throws Exception {
		this(img, new float[]{scaleX, shearX, 0, translateX,
				  			   shearY, scaleY, 0, translateY,
				  			   0, 0, 1, 0}, mode, new OutOfBoundsConstantValueFactory<T,Img<T>>((T)withValue(img, img.firstElement().createVariable(), outside)));
	}

	public AbstractAffine3D(final Img<T> img,
			final float scaleX, final float shearX,
			final float shearY, final float scaleY,
			final float translateX, final float translateY,
			final Mode mode, final OutOfBoundsFactory<T,Img<T>> oobf) throws Exception {
		this(img, new float[]{scaleX, shearX, 0, translateX,
				  			   shearY, scaleY, 0, translateY,
				  			   0, 0, 1, 0}, mode, oobf);
	}
	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static final NumericType<?> withValue(final Img<? extends NumericType<?>> img, final NumericType<?> type, final Number val) {
		final NumericType t = img.firstElement().createVariable();
		if (ARGBType.class.isAssignableFrom(t.getClass())) {
			int i = val.intValue();
			t.set((NumericType)new ARGBType(i));
		} else {
			((RealType)t).setReal(val.doubleValue());
		}
		return t;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static private final <N extends NumericType<N>>
						Img<N> process(final Img<N> img, final float[] matrix,
						final Mode mode, final OutOfBoundsFactory<N,Img<N>> oobf) throws Exception {
		if (matrix.length < 12) {
			throw new IllegalArgumentException("Affine transform in 2D requires a matrix array of 12 elements.");
		}
		final Type<?> type = img.firstElement().createVariable();
		if (ARGBType.class.isAssignableFrom(type.getClass())) { // type instanceof RGBALegacyType fails to compile
			return (Img)processRGBA((Img)img, matrix, mode, (OutOfBoundsFactory)oobf);
		} else if (type instanceof RealType<?>) {
			return (Img)processReal((Img)img, matrix, mode, (OutOfBoundsFactory)oobf);
		} else {
			throw new Exception("Affine transform: cannot handle type " + type.getClass());
		}
	}

	@SuppressWarnings("unchecked")
	static private final Img<ARGBType> processRGBA(final Img<ARGBType> img, final float[] m,
			final Mode mode, final OutOfBoundsFactory<ARGBType,Img<ARGBType>> oobf) throws Exception {
		// Process each channel independently and then compose them back
		OutOfBoundsFactory<FloatType,Img<FloatType>> ored, ogreen, oblue, oalpha;
		if (OutOfBoundsConstantValueFactory.class.isAssignableFrom(oobf.getClass())) { // can't use instanceof
			final int val = ((OutOfBoundsConstantValueFactory<ARGBType,Img<ARGBType>>)oobf).getValue().get();
			ored = new OutOfBoundsConstantValueFactory<FloatType,Img<FloatType>>(new FloatType((val >> 16) & 0xff));
			ogreen = new OutOfBoundsConstantValueFactory<FloatType,Img<FloatType>>(new FloatType((val >> 8) & 0xff));
			oblue = new OutOfBoundsConstantValueFactory<FloatType,Img<FloatType>>(new FloatType(val & 0xff));
			oalpha = new OutOfBoundsConstantValueFactory<FloatType,Img<FloatType>>(new FloatType((val >> 24) & 0xff));
		} else {
			// Jump into the pool!
			try {
				ored = oobf.getClass().newInstance();
			} catch (Exception e) {
				System.out.println("Affine3D for RGBA: oops -- using a black OutOfBoundsStrategyValueFactory");
				ored = new OutOfBoundsConstantValueFactory<FloatType,Img<FloatType>>(new FloatType());
			}
			ogreen = ored;
			oblue = ored;
			oalpha = ored;
		}
		return new RGBA(processReal(Compute.inFloats(new Red(img)), m, mode, ored),
						processReal(Compute.inFloats(new Green(img)), m, mode, ogreen),
						processReal(Compute.inFloats(new Blue(img)), m, mode, oblue),
						processReal(Compute.inFloats(new Alpha(img)), m, mode, oalpha)).asImage();
	}

	static private final <R extends RealType<R>> Img<R> processReal(final Img<R> img, final float[] m,
			final Mode mode, final OutOfBoundsFactory<R,Img<R>> oobf) throws Exception {
		final InterpolatorFactory<R,Img<R>> inter;
		switch (mode) {
		case LINEAR:
			inter = new NLinearInterpolatorFactory<R,Img<R>>(oobf);
			break;
		case NEAREST_NEIGHBOR:
			inter = new NearestNeighborInterpolatorFactory<R>(oobf);
			break;
		default:
			throw new IllegalArgumentException("Scale: don't know how to scale with mode " + mode);
		}

		final ImageTransform<R> transform;

		if (2 == img.numDimensions()) {
			// Transform the single-plane image in 2D
			AffineModel2D aff = new AffineModel2D();
			aff.set(m[0], m[4], m[1], m[5], m[3], m[7]);
			transform = new ImageTransform<R>(img, aff, inter);
		} else if (3 == img.numDimensions()) {
			// Transform the image in 3D, or each plane in 2D
			if (m.length < 12) {
				throw new IllegalArgumentException("Affine transform in 3D requires a matrix array of 12 elements.");
			}
			AffineModel3D aff = new AffineModel3D();
			aff.set(m[0], m[1], m[2], m[3],
					m[4], m[5], m[6], m[7],
					m[8], m[9], m[10], m[11]);
			transform = new ImageTransform<R>(img, aff, inter);
			// Ensure Z dimension is not altered if scaleZ is 1:
			if (Math.abs(m[10] - 1.0f) < 0.000001 && 0 == m[8] && 0 == m[9]) {
				long[] d = transform.getNewImageSize();
				d[2] = img.dimension(2); // 0-based: '2' is the third dimension
				transform.setNewImageSize(d);
			}
		} else {
			throw new Exception("Affine transform: only 2D and 3D images are supported.");
		}

		if (!transform.checkInput() || !transform.process()) {
			throw new Exception("Could not affine transform the image: " + transform.getErrorMessage());
		}

		return transform.getResult();
	}
}