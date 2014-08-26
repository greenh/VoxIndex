


### The Problem
VoxIndex deals with a problem I like to think of as _lookup_. To a first approximation, 
lookup is kind of like search, in that both search and lookup involve locating information
on the web; but the similarity is really only on the surface.

In search, there's an up-front presumption that you know (more or less) what you're 
looking for, but you don't know where it is. So, you fire off a query to Google, say, 
and returns a few million possible locations for your viewing pleasure, and, well, you know
the drill. 

But let's think about a different scenario. Suppose you're programming in, say, Java, and 
you need some information about a particular method of a particular Java class. Since I've
done a lot of Java programming, I already know where the required information is, and how
to get to it. Actually getting to it, though, is the problem.

So, why is it a problem? Well, in a typical case, I would:

1. Open a browser tab and load a the top level of a Javadoc tree.
2. Find the class I'm interested in. Either...
   * Search through the long list (~4000 entries) of class names until I
located the one you're intested in.
   * Or, if I happen to know the package the class is in, I can search through the shorter
	list of packages and click on the one containing the class. This gets me 
	a usually fairly short list of class names to search through.
3. Click on the class, and the class-specific page appears. 
4. Scroll down to the list of methods, find the one you're looking for.

Not bad, huh? 

Actually, I think this is pretty awful. Obviously, on an occasional basis, 
it's no big deal. But repeat the process frequently enough and all that time 
starts adding up. 

But there's a bigger problem. I'm frequently deep in the middle of some thought
process when the 
urge to look at some bit of documentation strikes. If I take the time to look up whatever 
it is, then  like as not I can kiss my train of thought goodbye. And there we have the 
_real_ problem: I really do not like to have to painstakingly reconstruct the
state of my head just because I needed some additional bit of information!
 

It's worth noting that lookup is actually a fairly general problem, of which the 
computer documentation example above is just one instance. Here's another use case.
I've done several educational forays into the some of the theoretical aspects of 
computer science and math, working from a textbook in each case. The nature of the 
material involves doing exercises that consist of proofs, proofs, and more proofs. 
Not having an eidetic memory,
as I inevitably find myself having to go back and forth in the book looking for 
details of definitions, theorems, lemmas, corollaries, exercises, and so on. 

So, I'm the middle of a crafting a proof (kinda like being in the middle of a writing an
intense piece of code), and I need to know... what's the definition of 'convergence'? 
Or what did theorem 3.7 say? Or what was Exercise 3.2.4?
  
Well, I know they're in chapter 3... somewhere. So, what do I do? Start flipping
pages? Maybe look in the table of contents. Or the index. More flipping of pages...

And at the end of such a lookup, more often than not, I'm left asking, "Uh, so what did
I want to look this up for?"... and then having to laboriously piece my train of thought back 
together again. And that _really_ hurts.

By now, hopefully the dimensions of the problem are becoming clear. I'm looking for
some way to get to bits of information that I _know_ are there, that I _can_ find all 
by my lonesome, but in a way that costs essentially nothing in terms of time, 
and even less in terms of mental effort. 

### The technology

So, all I want is for my computer to read my mind, and then dutifully display just 
exactly what I want to see, right? Pretty close, but the mind-reading part isn't exactly 
off-the-shelf technology yet. But there's an alternative: I've got my voice, which 
I can use to generate speech. It's a resource 
not much utilized when I'm typing away furiously (or thinking about what to type next). 
So, why not speak my desires, and the computer take care of the details of looking 
the stuff up?  

So there's the outline of the plan. 

* We digest one or more bodies of information into a set of 
items we'd plausibly want to look up. Each item has a location associated with it,
generally a URL (but if we're dealing with a dead-tree book, the location
could be a page number).

* For each item, we associate some vocalizable tag or phrase. For something like
Java classes or methods, this would probably be something like the class or method name. 
For a book, maybe something like "chapter 3" or "definition of convergence".

* We then assemble the phrases into a grammar, and hand the grammar to 
a speech recognition (SR) engine. Currently, we embed the SR engine in a web server.

* On the front end, we have a browser or browser-like app which listens for 
candidate chunks of speech, records them, and fires them off to the server.

* The server does the SR thing on the recorded audio, and returns the results to the 
front end.

* If recognition occured, the front end loads the information
indicated by the recognition results, displays it, and generally acts browser-like.
 
The salient thing to recognize here is the extent of involvement that I, as a user, 
have to put into the lookup process: as I encounter some data-bite I need to look
up, I merely speak its identifying phrase, and continue on my (undisturbed) merry 
way until the results appear.   
  
Or something like that.






