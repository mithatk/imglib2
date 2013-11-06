package net.imglib2.display.projectors;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import net.imglib2.view.Views;

/**
 * A general 2D Projector that uses two dimensions as input to create the 2D
 * result. Starting from the reference point two dimensions are sampled such
 * that a plain gets cut out of a higher dimensional data volume. <br>
 * The mapping function can be specified with a {@link Converter}. <br>
 * A basic example is cutting out a time frame from a (greyscale) video
 * 
 * @author Michael Zinsmaier, Martin Horn, Christian Dietz
 * 
 * @param <A>
 * @param <B>
 */
public class Projector2D< A, B > extends AbstractProjector2D< A, B >
{

	final protected Converter< ? super A, B > converter;

	final protected RandomAccessible< A > source;

	final protected IterableInterval< B > target;

	final int numDimensions;

	private final int dimX;

	private final int dimY;

	protected final int X = 0;

	protected final int Y = 1;

	/**
	 * creates a new 2D projector that samples a plain in the dimensions dimX,
	 * dimY.
	 * 
	 * @param dimX
	 * @param dimY
	 * @param source
	 * @param target
	 * @param converter
	 *            a converter that is applied to each point in the plain. This
	 *            can e.g. be used for normalization, conversions, ...
	 */
	public Projector2D( final int dimX, final int dimY, final RandomAccessible< A > source, final IterableInterval< B > target, final Converter< ? super A, B > converter )
	{
		super( source.numDimensions() );
		this.dimX = dimX;
		this.dimY = dimY;
		this.target = target;
		this.source = source;
		this.converter = converter;
		this.numDimensions = source.numDimensions();
	}

	/**
	 * projects data from the source to the target and applies the former
	 * specified {@link Converter} e.g. for normalization.
	 */
	@Override
	public void map()
	{
		// fix interval for all dimensions
		for ( int d = 0; d < position.length; ++d )
			min[ d ] = max[ d ] = position[ d ];

		min[ dimX ] = target.min( X );
		min[ dimY ] = target.min( Y );
		max[ dimX ] = target.max( X );
		max[ dimY ] = target.max( Y );

		// TODO: this is ugly, but the only way to make sure, that iteration
		// order fits in the case of one sized dims. Tobi?
		IterableInterval< A > srcIterableForCursor = Views.iterable( Views.interval( source, new FinalInterval( min, max ) ) );
		IterableInterval< A > srcIterableForIterationOrder = Views.iterable( clean( Views.interval( source, new FinalInterval( min, max ) ) ) );

		final Cursor< B > targetCursor = target.localizingCursor();
		final Cursor< A > sourceCursor = srcIterableForCursor.cursor();

		if ( target.iterationOrder().equals( srcIterableForIterationOrder.iterationOrder() ) && !( sourceCursor instanceof RandomAccessibleIntervalCursor ) )
		{
			// use cursors

			while ( targetCursor.hasNext() )
			{
				converter.convert( sourceCursor.next(), targetCursor.next() );
			}
		}
		else
		{
			// use localizing cursor
			RandomAccess< A > sourceRandomAccess = source.randomAccess();
			sourceRandomAccess.setPosition( position );
			while ( targetCursor.hasNext() )
			{
				final B b = targetCursor.next();
				sourceRandomAccess.setPosition( targetCursor.getLongPosition( X ), dimX );
				sourceRandomAccess.setPosition( targetCursor.getLongPosition( Y ), dimY );

				converter.convert( sourceRandomAccess.get(), b );
			}
		}
	}

	private < T > IntervalView< T > clean( IntervalView< T > in )
	{
		IntervalView< T > res = in;

		for ( int d = in.numDimensions() - 1; d >= 0; --d )
			if ( in.dimension( d ) == 1 && res.numDimensions() > 1 )
				res = Views.hyperSlice( res, d, 0 );

		return res;
	}

}
