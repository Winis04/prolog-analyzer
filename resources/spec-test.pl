:- module(spec_test,[]).
:- use_module("../prolog/annotations",[spec_pre/2,spec_post/3,declare_spec/1,define_spec/2]).

:- declare_spec(intOrVar).
:- declare_spec(foo).
:- declare_spec(a).
:- declare_spec(b).
:- declare_spec(c).
:- declare_spec(tree(specvar(X))).

:- define_spec(foo,compound(foo(int,int))).
:- define_spec(intOrVar,one_of([int,var])).

:- define_spec(a,and([int,atom])).
:- define_spec(b,tuple([int,var])).
:- define_spec(c,atom(empty)).

:- define_spec(tree(specvar(X)),one_of([compound(node(tree(specvar(X)),specvar(X),tree(specvar(X)))),atom(empty)])).

:- spec_pre(member_int/2,[int,list(int)]).
:- spec_pre(member_int/2,[var,list(int)]).
:- spec_post(member_int/2,[],[[0:int,1:list(int)]]).
:- spec_invariant(member_int/2,[any,ground]).
member_int(H,[H,_]) :- !.
member_int(E,[_,T]) :-
    member_int(E,T).


:- spec_pre(foo/3,[foo,intOrVar,intOrVar]).
:- spec_post(foo/3,[],[[0:foo,1:int,2:int]]).
foo(foo(A,B),A,B).
