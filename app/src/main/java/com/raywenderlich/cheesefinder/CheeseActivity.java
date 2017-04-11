package com.raywenderlich.cheesefinder;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class CheeseActivity extends BaseSearchActivity {

    private Disposable mDisposable;

    @Override
    protected void onStart() {
        super.onStart();

        // create an observable by calling the method you just wrote.
        Observable<String> buttonClickStream = createButtonClickObservable();
        Observable<String> textChangeStream = createTextChangeObservable();

        // I merge between 2 observable to use them both
        Observable<String> searchTextObservable = Observable.merge(textChangeStream, buttonClickStream);

        mDisposable = searchTextObservable
                .observeOn(AndroidSchedulers.mainThread())
                // will be called every time a new item is emitted
                .doOnNext(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        showProgressBar();
                    }
                })
                // specify that the next operator should be called on the I/O thread
                .observeOn(Schedulers.io())
                // Any thing i want to do with data i use map method
                // For each search query, you return a list of results
                .map(new Function<String, List<String>>() {
                    @Override
                    public List<String> apply(String query) {
                        return mCheeseSearchEngine.search(query);
                    }
                })
                //specify that code down the chain should be executed on the main thread instead of on the I/O thread
                //In Android, all code that works with Views should execute on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                // Subscribe to the observable with subscribe(), and supply a simple Consumer
                .subscribe(new Consumer<List<String>>() {
                    // will be called when the observable emits an item
                    @Override
                    public void accept(List<String> result) {
                        hideProgressBar();
                        showResult(result);
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        //The Observable.subscribe() call returns a Disposable
        // we make dispose to finish the calling
        if (!mDisposable.isDisposed()) {
            mDisposable.dispose();
        }
    }

    // a method that returns an observable that will emit strings.
    private Observable<String> createButtonClickObservable() {

        // create an observable with Observable.create(), and supply it with a new ObservableOnSubscribe.
        return Observable.create(new ObservableOnSubscribe<String>() {

            // define your ObservableOnSubscribe by overriding subscribe()
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {
                // Set up an OnClickListener on mSearchButton.
                mSearchButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // When the click event happens
                        // call onNext on the emitter and pass it the current text value of mQueryEditText.
                        emitter.onNext(mQueryEditText.getText().toString());
                    }
                });

                // It’s a useful habit to remove listeners as soon as they are no longer needed
                //and your implementation will be called when the Observable is disposed,
                // such as when the Observable is completed or all Observers have unsubscribed from it.
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        // removes the listener
                        mSearchButton.setOnClickListener(null);
                    }
                });
            }
        });
    }


    //Declare a method that will return an observable for text changes
    private Observable<String> createTextChangeObservable() {
        //Create textChangeObservable with create(), which takes an ObservableOnSubscribe
        Observable<String> textChangeObservable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {
                //When an observer makes a subscription, the first thing to do is to create a TextWatcher
                final TextWatcher watcher = new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }

                    // When the user types and onTextChanged() triggers, you pass the new text value to an observer
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        emitter.onNext(s.toString());
                    }
                };

                //Add the watcher to your TextView by calling addTextChangedListener()
                // out of RxJava
                mQueryEditText.addTextChangedListener(watcher);

                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        mQueryEditText.removeTextChangedListener(watcher);
                    }
                });
            }
        });
        //return the created observable
        return textChangeObservable
                //filter data to make search start at length of 2 character
                .filter(new Predicate<String>() {
                    @Override
                    public boolean test(String query) throws Exception {
                        return query.length() >= 2;
                    }
                    //You don’t want to send a new request to the server every time the query is changed by two symbol
                    //waits for a specified amount of time after each item emission for another item
                    //If no item happens to be emitted during this wait, the last item is finally emitted
                }).debounce(1000, TimeUnit.MILLISECONDS);
    }


}
