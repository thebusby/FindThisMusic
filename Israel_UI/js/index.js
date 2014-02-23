/*forked from a dribble project*/

    

$(document).ready(function(){
	var cssStyle = { 'margin-top' : - $('.this').size.height / 2 + 'px'	};
	var imgx = $('thumbnail').length;
	var x = $('audio').length;
	var z=0;
	$('.wrapper > img').attr("src", function() { return $('.thumbnail')[z].src;
}); 
	$( ".wrapper" ).wrap($('<div class="slice-content"/>'))
	.parent()
	.clone()
	.appendTo( ".this" )
	.find( '.wrapper' )
	.css('margin-top','-111px');
	$('.slice-content').click(function(){
		$('.slice-content:nth-child(4)').toggleClass('flip');
	});
	
	$('.play').click(function(){
		$('audio')[z].play();						
		$(this).toggleClass('pause');
		if($(this).hasClass('pause')){
		$('audio')[z].pause();
		}
	});
	$('.next').click(function(){
		$('audio')[z].pause();
		$('.play').removeClass('pause');
		$('audio')[z].currentTime=0;
		z++;
		if(z>=x){z=0}
		$('.wrapper > img').attr("src", function() { return $('.thumbnail')[z].src;
});
		$('audio')[z].play();
	});
	$('.previous').click(function(){
		$('audio')[z].pause();
		$('.play').removeClass('pause');
		$('audio')[z].currentTime=0;
		z--;
		if(z<0){z=x-1}
		$('.wrapper > img').attr("src", function() { return $('.thumbnail')[z].src;
});
		$('audio')[z].play();
	});
	$('audio').bind("ended",function(){
		z++;
		if(z>=x){z=0}
		$('.wrapper > img').attr("src", function() { return $('.thumbnail')[z].src;
});
		$('audio')[z].play();
	});
	// Like & Share screens fade
	$('.likescreen, .sharescreen, .blackscreen, .sharetext').hide();
	$('.like').click(function(){
		$('.slice-content:nth-child(4)').toggleClass('flip');
		$('.blackscreen').fadeIn(1000);
		$('.likescreen').fadeIn(500);
		$('.likecounter').addClass('dropdown');
	});
	$('.likescreen').click(function(){
		$('.likescreen, .blackscreen').fadeOut(300);
		$('.likecounter').removeClass('dropdown');
	});
	$('.share').click(function(){
		$('.slice-content:nth-child(4)').toggleClass('flip');
		$('.blackscreen').fadeIn(1000);
		$('.sharescreen').fadeIn(500);
		$('.socialicons').addClass('dropdown');
		setTimeout(function(){
			$('.sharetext').fadeIn(300);
			$('.facebook').css('right','130px').css('opacity','1');
			$('.dribbble').css('left','130px').css('opacity','1');
			setTimeout(function(){
				$('.lineone, .linetwo').css('width','19px');
			},300);
		},1000);
	});
	$('.sharescreen').click(function(){
		$('.slice-content:nth-child(4)').toggleClass('flip');
		$('.sharescreen, .blackscreen, .sharetext').fadeOut();
		$('.socialicons').removeClass('dropdown');
		$('.lineone, .linetwo').css('width','0px');
		$('.facebook').css('right','0').css('opacity','0');
		$('.dribbble').css('left','0').css('opacity','0');
	});



	$("ul > li, .share, .like").find('span').hide().end()
	.hover(function(){
		$(this).find('span').stop(true, true).fadeIn(200)
	}, function(){
		$(this).find('span').stop(true, true).fadeOut(200)
	});

});

